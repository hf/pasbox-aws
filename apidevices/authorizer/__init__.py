import boto3
import os.path
import subprocess

import awacs
import awacs.sts

from troposphere import Template, Parameter, Output, Export, Sub, Ref, GetAtt
from troposphere import awslambda, iam


def generate(template):
    artifact_bucket = template.add_parameter(
        Parameter('AuthorizerArtifactBucket', Type='String'))

    artifact_name = template.add_parameter(
        Parameter('AuthorizerArtifactName', Type='String'))

    artifact_version = template.add_parameter(
        Parameter('AuthorizerArtifactVersion', Type='String'))

    fnrole = template.add_resource(
        iam.Role(
            'DeviceAuthorizerFnRole',
            RoleName='DeviceAuthorizerFnRole',
            ManagedPolicyArns=[
                "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
            ],
            AssumeRolePolicyDocument=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.sts.AssumeRole],
                        Principal=awacs.aws.Principal("Service",
                                                      "lambda.amazonaws.com"),
                    )
                ])))

    lambdafn = template.add_resource(
        awslambda.Function(
            'DeviceAuthorizerFn',
            FunctionName='DeviceAuthorizerFn',
            Runtime='nodejs10.x',
            MemorySize=128,
            Timeout=10,
            Handler='index.handler',
            Role=GetAtt(fnrole, "Arn"),
            Code=awslambda.Code(
                S3Bucket=Ref(artifact_bucket),
                S3Key=Ref(artifact_name),
                S3ObjectVersion=Ref(artifact_version),
            )))

    template.add_output(
        Output(
            'DeviceAuthorizerFn',
            Export=Export('DeviceAuthorizerFn'),
            Value=Ref(lambdafn)))

    template.add_output(
        Output(
            'DeviceAuthorizerFnArn',
            Export=Export('DeviceAuthorizerFnArn'),
            Value=GetAtt(lambdafn, "Arn")))

    return lambdafn


def pre_deploy(_args):
    for command in [
            "yarn install --pure-lockfile", "yarn build", "yarn artifact"
    ]:
        path = os.path.relpath(os.path.dirname(__file__))
        print(">>> run '" + command + "' in '" + path + "'")

        process = subprocess.Popen(command, cwd=path, shell=True)
        process.wait()

        if 0 != process.returncode:
            exit(process.returncode)


def deploy(args):
    bucket = 'artifacts-' + args.region + '-' + args.account
    key = 'apidevices-authorizer.zip'

    s3_client = boto3.client('s3')

    artifact = open(
        os.path.relpath(
            os.path.join(os.path.dirname(__file__), "artifact.zip")), "rb")
    artifact_object = s3_client.put_object(
        Bucket=bucket, Key=key, Body=artifact)
    artifact.close()

    version = artifact_object['VersionId']

    return [
        dict(ParameterKey='AuthorizerArtifactBucket', ParameterValue=bucket),
        dict(ParameterKey='AuthorizerArtifactName', ParameterValue=key),
        dict(ParameterKey='AuthorizerArtifactVersion', ParameterValue=version)
    ]
