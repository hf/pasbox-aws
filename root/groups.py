import awacs
import awacs.logs
import awacs.sts
import awacs.iam

from troposphere import Template, Ref, Sub, GetAtt
from troposphere import iam


def generate(template):
    group_administrators = template.add_resource(
        iam.Group(
            'IAMGroupAdministrators',
            GroupName='Administrators',
            ManagedPolicyArns=[
                'arn:aws:iam::aws:policy/AdministratorAccess',
            ],
        ))

    group_engineers = template.add_resource(
        iam.Group(
            'IAMGroupEngineers',
            GroupName='Engineers',
            ManagedPolicyArns=[
                'arn:aws:iam::aws:policy/ReadOnlyAccess',
            ]))

    policy_assume_role_only = template.add_resource(
        iam.ManagedPolicy(
            'PolicyAssumeRoleOnly',
            ManagedPolicyName='AssumeRoleOnly',
            Description='Allows users to only call sts:AssumeRole.',
            PolicyDocument=awacs.aws.PolicyDocument(
                Version='2012-10-17',
                Statement=[
                    awacs.aws.Statement(
                        Effect=awacs.aws.Allow,
                        Action=[awacs.sts.AssumeRole],
                        Resource=["*"],
                    )
                ])))

    group_robots = template.add_resource(
        iam.Group(
            'IAMGroupRobots',
            GroupName='Robots',
            ManagedPolicyArns=[Ref(policy_assume_role_only)]))
