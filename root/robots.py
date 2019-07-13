import awacs
import awacs.logs
import awacs.sts

from troposphere import Template, Ref, Sub, GetAtt, Output
from troposphere import iam


def generate(template):
    deployment_robot = template.add_resource(
        iam.User(
            'DeploymentRobot',
            UserName="DeploymentRobot",
            Groups=["Robots"],
        ))

    deployment_robot_access_key = template.add_resource(
        iam.AccessKey(
            'DeploymentRobotAcessKey',
            DependsOn=[deployment_robot],
            Serial=0,
            Status='Active',
            UserName=Ref(deployment_robot),
        ))

    template.add_output(
        Output(
            'DeploymentRobotAccessKey',
            Value=Ref(deployment_robot_access_key)))

    template.add_output(
        Output(
            'DeploymentRobotSecretAccessKey',
            Value=GetAtt(deployment_robot_access_key, "SecretAccessKey")))
