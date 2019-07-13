import boto3
import os.path
import subprocess

import awacs
import awacs.sts

from troposphere import Template, Output, Export, ImportValue, Ref, Sub, GetAtt
from troposphere import apigateway, awslambda, iam, dynamodb

from apidevices import authorizer
from apidevices import androidregister


def lambda_invocation_arn(lambda_resource):
    return Sub(
        str.join(':', [
            "arn", "aws", "apigateway", "${AWS::Region}", "lambda",
            "path/2015-03-31/functions/${fn}/invocations"
        ]),
        fn=GetAtt(lambda_resource, "Arn"))


def executeapi_arn(api_resource, path):
    return Sub(
        str.join(':', [
            'arn', 'aws', 'execute-api', '${AWS::Region}', '${AWS::AccountId}',
            '${api}/' + path
        ]),
        api=Ref(api_resource))


template = Template()

devices_table = template.add_resource(
    dynamodb.Table(
        'DevicesTable',
        TableName='Devices',
        KeySchema=[
            dynamodb.KeySchema(AttributeName='key', KeyType='HASH'),
            dynamodb.KeySchema(AttributeName='type', KeyType='RANGE'),
        ],
        AttributeDefinitions=[
            dynamodb.AttributeDefinition(
                AttributeName='key', AttributeType='S'),
            dynamodb.AttributeDefinition(
                AttributeName='type', AttributeType='S'),
        ],
        BillingMode='PAY_PER_REQUEST',
    ))

restapi = template.add_resource(
    apigateway.RestApi(
        'DevicesApi',
        Name='DevicesApi',
        Description='Devices API.',
        BinaryMediaTypes=['application/vnd.pasbox.octets']))

authorizer_lambda = authorizer.generate(template)

authorizer_credentials = template.add_resource(
    iam.Role(
        'DevicesApiAuthorizerCredentials',
        RoleName='DevicesApiAuthorizerCredentials',
        AssumeRolePolicyDocument=awacs.aws.PolicyDocument(
            Version='2012-10-17',
            Statement=[
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[awacs.sts.AssumeRole],
                    Principal=awacs.aws.Principal("Service",
                                                  "apigateway.amazonaws.com"),
                )
            ]),
        ManagedPolicyArns=[
            "arn:aws:iam::aws:policy/service-role/AWSLambdaRole",
        ]))

devices_authorizer = template.add_resource(
    apigateway.Authorizer(
        'DevicesApiAuthorizer',
        RestApiId=Ref(restapi),
        Name='DevicesApiAuthorizer',
        Type='REQUEST',
        AuthorizerResultTtlInSeconds=0,
        AuthorizerCredentials=GetAtt(authorizer_credentials, "Arn"),
        IdentitySource="method.request.header.Attestation",
        AuthorizerUri=lambda_invocation_arn(authorizer_lambda)))

android_resource = template.add_resource(
    apigateway.Resource(
        'AndroidResource',
        RestApiId=Ref(restapi),
        PathPart='android',
        ParentId=GetAtt(restapi, 'RootResourceId')))

root_method = template.add_resource(
    apigateway.Method(
        'RootMethod',
        RestApiId=Ref(restapi),
        ResourceId=GetAtt(restapi, 'RootResourceId'),
        HttpMethod='POST',
        AuthorizationType='CUSTOM',
        AuthorizerId=Ref(devices_authorizer),
        Integration=apigateway.Integration(
            Type='MOCK',
            IntegrationHttpMethod='POST',
        )))

android_register_resource = template.add_resource(
    apigateway.Resource(
        'AndroidRegisterResource',
        RestApiId=Ref(restapi),
        ParentId=Ref(android_resource),
        PathPart='register'))

android_register_lambda = androidregister.generate(template)

android_register_permission = template.add_resource(
    awslambda.Permission(
        'AndroidRegisterPermission',
        FunctionName=Ref(android_register_lambda),
        Action='lambda:InvokeFunction',
        Principal='apigateway.amazonaws.com',
        SourceArn=executeapi_arn(restapi, '*/*/*'),
    ))

android_register_method = template.add_resource(
    apigateway.Method(
        'AndroidRegisterMethod',
        RestApiId=Ref(restapi),
        ResourceId=Ref(android_register_resource),
        HttpMethod='POST',
        AuthorizationType='NONE',
        Integration=apigateway.Integration(
            Type='AWS_PROXY',
            IntegrationHttpMethod='POST',
            Uri=lambda_invocation_arn(android_register_lambda),
        )))

deployment_v1 = template.add_resource(
    apigateway.Deployment(
        'AndroidRestApiV1',
        DependsOn=[root_method, android_register_method],
        RestApiId=Ref(restapi),
        StageName='v1',
        StageDescription=apigateway.StageDescription(
            Description='Devices API version 1.')))

template.add_output(
    Output(
        'DevicesApiId',
        Description='Devices REST API ID.',
        Value=Ref(restapi),
        Export=Export('DevicesApiId')))


def pre_deploy(args):
    authorizer.pre_deploy(args)
    androidregister.pre_deploy(args)


def deploy(args):
    params = []

    for param in authorizer.deploy(args):
        params.append(param)

    for param in androidregister.deploy(args):
        params.append(param)

    return params
