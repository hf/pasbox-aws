import boto3
import time
import argparse
import os.path
import importlib
import yaml

parser = argparse.ArgumentParser(description='Deploy a stack.')
parser.add_argument(
    '--region',
    type=str,
    dest='region',
    required=True,
    help='the AWS region that this deployment is occuring in')
parser.add_argument(
    '--account',
    type=str,
    dest='account',
    required=True,
    help='the AWS account ID')
parser.add_argument(
    '--stack',
    type=str,
    dest='stack',
    required=True,
    help='the stack name, a file stack.py.yaml should exist')
parser.add_argument(
    '--execute',
    type=bool,
    dest='execute',
    required=False,
    default=False,
    help='whether to execute the change immediately')

args = parser.parse_args()

mod = importlib.import_module(args.stack)

template = getattr(mod, "template")
template_yaml = template.to_yaml()

parameters = []

cloudformation_client = boto3.client('cloudformation')
cloudformation_resource = boto3.resource('cloudformation')

if hasattr(mod, "pre_deploy"):
    print(">>> pre deploy")
    ret = getattr(mod, "pre_deploy")(args)

    if isinstance(ret, list):
        for item in ret:
            parameters.append(item)

if hasattr(mod, "deploy"):
    print(">>> deploy")
    ret = getattr(mod, "deploy")(args)

    if isinstance(ret, list):
        for item in ret:
            parameters.append(item)

print(">>> looking for existing stack")

all_stacks = cloudformation_resource.stacks.all()
stack = None

for existing_stack in all_stacks:
    if args.stack == existing_stack.name:
        stack = existing_stack
        print(">>> found existing stack")
        break

if stack and stack.stack_status in ['REVIEW_IN_PROGRESS']:
    print(">>> stack is in review")
    stack = None

print(">>> creating change set")

change_set_name = args.stack + "-" + str(int(time.time()))
change_set = cloudformation_client.create_change_set(
    ChangeSetName=change_set_name,
    StackName=args.stack,
    TemplateBody=template_yaml,
    ChangeSetType='CREATE' if not stack else 'UPDATE',
    Parameters=parameters,
    Capabilities=['CAPABILITY_NAMED_IAM'])

print(">>> change set ready")
print(yaml.dump(change_set))

if args.execute:
    print(">>> wait for change set")
    description = cloudformation_client.describe_change_set(
        StackName=args.stack, ChangeSetName=change_set_name)

    waiter = cloudformation_client.get_waiter('change_set_create_complete')
    waiter.wait(ChangeSetName=change_set_name, StackName=args.stack)

    print(">>> execute change set")

    description = cloudformation_client.describe_change_set(
        StackName=args.stack, ChangeSetName=change_set_name)

    print(yaml.dump(description))

    cloudformation_client.execute_change_set(
        StackName=args.stack, ChangeSetName=change_set_name)

if hasattr(mod, "post_deploy"):
    print(">>> post deploy")
    getattr(mod, "post_deploy")(args)
