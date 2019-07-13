import awacs
import awacs.logs
import awacs.sts
import awacs.iam
import awacs.s3
import awacs.kms

from troposphere import Template, Ref, Sub, GetAtt
from troposphere import iam
from troposphere import s3
from troposphere import kms


def generate(template):
    artifacts_key = template.add_resource(
        kms.Key(
            'ArtifactsKey',
            Description='Key used to encrypt artifacts buckets.',
            Enabled=True,
            KeyPolicy=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Principal=awacs.aws.Principal(
                            "AWS", Sub("arn:aws:iam::${AWS::AccountId}:root")),
                        Action=[awacs.aws.Action("kms", "*")],
                        Resource=["*"],
                    ),
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Principal=awacs.aws.Principal("Service",
                                                      "s3.amazonaws.com"),
                        Action=[
                            awacs.kms.Encrypt, awacs.kms.Decrypt,
                            awacs.kms.ReEncrypt, awacs.kms.GenerateDataKey,
                            awacs.kms.DescribeKey
                        ],
                        Resource=["*"],
                    )
                ])))

    artifacts_key_alias = template.add_resource(
        kms.Alias(
            'ArtifactsKeyAlias',
            AliasName='alias/ArtifactsKey',
            TargetKeyId=Ref(artifacts_key)))

    artifacts_bucket = template.add_resource(
        s3.Bucket(
            'BucketArtifacts',
            DependsOn=[artifacts_key, artifacts_key_alias],
            DeletionPolicy='Delete',
            BucketName=Sub('artifacts-${AWS::Region}-${AWS::AccountId}'),
            BucketEncryption=s3.BucketEncryption(
                ServerSideEncryptionConfiguration=[
                    s3.ServerSideEncryptionRule(
                        ServerSideEncryptionByDefault=s3.
                        ServerSideEncryptionByDefault(
                            KMSMasterKeyID=Ref(artifacts_key),
                            SSEAlgorithm="aws:kms"))
                ]),
            VersioningConfiguration=s3.VersioningConfiguration(
                Status='Enabled'),
            PublicAccessBlockConfiguration=s3.PublicAccessBlockConfiguration(
                BlockPublicAcls=True,
                BlockPublicPolicy=True,
                IgnorePublicAcls=True,
                RestrictPublicBuckets=True,
            )))

    artifacts_bucket_policy = template.add_resource(
        s3.BucketPolicy(
            'BucketPolicyArtifacts',
            DependsOn=[artifacts_bucket],
            Bucket=Ref(artifacts_bucket),
            PolicyDocument=awacs.aws.PolicyDocument(Statement=[
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[
                        awacs.aws.Action("s3", "GetBucket*"),
                        awacs.aws.Action("s3", "ListBucket*"),
                    ],
                    Resource=[
                        Sub("arn:aws:s3:::artifacts-${AWS::Region}-${AWS::AccountId}"
                            )
                    ],
                    Principal=awacs.aws.Principal(
                        "AWS", Sub(
                            "arn:aws:iam::${AWS::AccountId}:role/Deploy"))),
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[
                        awacs.aws.Action("s3", "GetBucket*"),
                        awacs.aws.Action("s3", "ListBucket*"),
                    ],
                    Resource=[
                        Sub("arn:aws:s3:::artifacts-${AWS::Region}-${AWS::AccountId}"
                            )
                    ],
                    Principal=awacs.aws.Principal(
                        "AWS",
                        Sub("arn:aws:iam::${AWS::AccountId}:role/CloudFormationDeploy"
                            ))),
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[
                        awacs.aws.Action("s3", "PutObject*"),
                        awacs.aws.Action("s3", "GetObject*"),
                    ],
                    Resource=[
                        Sub("arn:aws:s3:::artifacts-${AWS::Region}-${AWS::AccountId}/*"
                            )
                    ],
                    Principal=awacs.aws.Principal(
                        "AWS", Sub(
                            "arn:aws:iam::${AWS::AccountId}:role/Deploy"))),
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[
                        awacs.aws.Action("s3", "PutObject*"),
                        awacs.aws.Action("s3", "GetObject*"),
                    ],
                    Resource=[
                        Sub("arn:aws:s3:::artifacts-${AWS::Region}-${AWS::AccountId}/*"
                            )
                    ],
                    Principal=awacs.aws.Principal(
                        "AWS",
                        Sub("arn:aws:iam::${AWS::AccountId}:role/CloudFormationDeploy"
                            ))),
            ])))
