import awacs
import awacs.logs
import awacs.sts
import awacs.iam
import awacs.s3
import awacs.logs

from troposphere import Template, Ref, Sub, GetAtt
from troposphere import iam


def generate(template):
    cloudformation_deploy_policy = template.add_resource(
        iam.ManagedPolicy(
            'CFDeployPolicy',
            ManagedPolicyName='CloudFormationDeployAccess',
            Description='Allows CloudFormation to deploy the application.',
            PolicyDocument=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.aws.Action("*")],
                        Resource=["*"],
                    ),
                ])))

    cloudformation_deploy_role = template.add_resource(
        iam.Role(
            'CFDeployRole',
            DependsOn=[cloudformation_deploy_policy],
            RoleName='CloudFormationDeploy',
            MaxSessionDuration=3600,
            ManagedPolicyArns=[
                Ref(cloudformation_deploy_policy),
                "arn:aws:iam::aws:policy/IAMReadOnlyAccess"
            ],
            AssumeRolePolicyDocument=awacs.aws.PolicyDocument(Statement=[
                awacs.aws.Statement(
                    Effect=awacs.aws.Allow,
                    Action=[awacs.sts.AssumeRole],
                    Principal=awacs.aws.Principal(
                        "Service", "cloudformation.amazonaws.com"))
            ])))

    deploy_policy = template.add_resource(
        iam.ManagedPolicy(
            'DeployPolicy',
            DependsOn=[cloudformation_deploy_role],
            ManagedPolicyName='DeployAccess',
            Description='Allows access for deploying the application.',
            PolicyDocument=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.aws.Action("cloudformation", "*")],
                        Resource=["*"],
                    ),
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[
                            awacs.aws.Action("s3", "Get*"),
                            awacs.s3.PutObject,
                            awacs.s3.ListBucket,
                        ],
                        Resource=["*"],
                    ),
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.iam.PassRole],
                        Resource=[GetAtt(cloudformation_deploy_role, "Arn")])
                ])))

    role_deploy = template.add_resource(
        iam.Role(
            'RoleDeploy',
            DependsOn=[deploy_policy],
            RoleName='Deploy',
            MaxSessionDuration=3600,
            ManagedPolicyArns=[Ref(deploy_policy)],
            AssumeRolePolicyDocument=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.sts.AssumeRole],
                        Principal=awacs.aws.Principal(
                            "AWS",
                            Sub("arn:aws:iam::${AWS::AccountId}:user/DeploymentRobot"
                                )))
                ])))
