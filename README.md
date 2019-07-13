Pasbox AWS Infrastructure
=========================

This is the Pasbox Infrastructure-as-Code setup for the AWS Cloud. Here are all
the steps necessary to bootstrap a new AWS account.

1. Create a new AWS account. Log in as the root user, add access keys to the
   root user and configure the AWS CLI for it and the region you want.
2. Install troposphere: `pip3 install troposphere[policy] boto3`
3. With `python3 deploy.py --stack <stack name> --region <AWS region> --account <AWS account> --execute` run:
3.1. As the root user deploy the `root` stack -- will setup the basics of the infrastructure.
3.2. Manually create accounts in the Administrators, Engineers and Robots IAM
 groups.
3.3. Lock the root account, switch to an account in Administrators.
3.4. Deploy the `pushnotifications` stack, execute the change set.
3.5. Manually create an SNS Platform Application for FCM with the exact name of `pasbox-android`. Set the
 attributes to the topics creted in the `pushnotifications` stack.
3.5. From this point on you can use accounts in the Robots IAM group.
3.6. You can now deploy the `apidevices` stack and execute the changeset.

