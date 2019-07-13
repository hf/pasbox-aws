import awacs
import awacs.sts
import boto3
import os.path
import subprocess
from troposphere import Parameter, Output, Export, Sub, Ref, GetAtt
from troposphere import awslambda, iam


def generate(template):
    artifact_bucket = template.add_parameter(
        Parameter('AndroidregisterArtifactBucket', Type='String'))

    artifact_name = template.add_parameter(
        Parameter('AndroidregisterArtifactName', Type='String'))

    artifact_version = template.add_parameter(
        Parameter('AndroidregisterArtifactVersion', Type='String'))

    fnrole = template.add_resource(
        iam.Role(
            'AndroidRegisterFnRole',
            RoleName='AndroidRegisterFnRole',
            ManagedPolicyArns=[
                "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole",
                "arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess",
                "arn:aws:iam::aws:policy/AmazonSNSFullAccess",
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
            'AndroidRegisterFn',
            FunctionName='AndroidRegisterFn',
            Runtime='java8',
            MemorySize=256,
            Timeout=30,
            Handler='dev.pasbox.apidevices.androidregister.AndroidRegister',
            Role=GetAtt(fnrole, "Arn"),
            Environment=awslambda.Environment(
                Variables=dict(
                    SNS_TOPIC_ANDROID_DEVICES_ARN=Sub("arn:aws:sns:${AWS::Region}:${AWS::AccountId}:devices-android"),
                    SNS_PLATFORM_APPLICATION_ANDROID_ARN=Sub(
                        'arn:aws:sns:${AWS::Region}:${AWS::AccountId}:app/GCM/pasbox-android'
                    ),
                    ANDROID_APP_PACKAGE_NAME="me.stojan.pasbox")),
            Code=awslambda.Code(
                S3Bucket=Ref(artifact_bucket),
                S3Key=Ref(artifact_name),
                S3ObjectVersion=Ref(artifact_version),
            )))

    template.add_output(
        Output(
            'AndroidRegisterFn',
            Export=Export('AndroidRegisterFn'),
            Value=Ref(lambdafn)))

    template.add_output(
        Output(
            'AndroidRegisterFnArn',
            Export=Export('AndroidRegisterFnArn'),
            Value=GetAtt(lambdafn, "Arn")))

    return lambdafn


def pre_deploy(_args):
    for command in [
        "./gradlew apidevices:androidregister:build"
    ]:
        path = os.path.join(os.path.relpath(os.path.dirname(__file__)), "..", "..")
        print(">>> run '" + command + "' in '" + path + "'")

        process = subprocess.Popen(command, cwd=path, shell=True)
        process.wait()

        if 0 != process.returncode:
            exit(process.returncode)


def deploy(args):
    bucket = 'artifacts-' + args.region + '-' + args.account
    key = 'apidevices-androidregister.zip'

    s3_client = boto3.client('s3')

    artifact = open(
        os.path.relpath(
            os.path.join(os.path.dirname(__file__), "build", "distributions", "androidregister-1.0-SNAPSHOT.zip")),
        "rb")
    artifact_object = s3_client.put_object(
        Bucket=bucket, Key=key, Body=artifact)
    artifact.close()

    version = artifact_object['VersionId']

    return [
        dict(
            ParameterKey='AndroidregisterArtifactBucket',
            ParameterValue=bucket),
        dict(ParameterKey='AndroidregisterArtifactName', ParameterValue=key),
        dict(
            ParameterKey='AndroidregisterArtifactVersion',
            ParameterValue=version)
    ]
