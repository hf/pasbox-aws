from troposphere import Template
from troposphere import sns

template = Template()

android = template.add_resource(
    sns.Topic('AndroidDevicesTopic',
              TopicName='devices-android'))

android_created = template.add_resource(
    sns.Topic('AndroidDevicesEndpointCreatedTopic',
              TopicName='devices-android-created'))

android_deleted = template.add_resource(
    sns.Topic('AndroidDevicesEndpointDeletedTopic',
              TopicName='devices-android-deleted'))

android_updated = template.add_resource(
    sns.Topic('AndroidDevicesEndpointUpdatedTopic',
              TopicName='devices-android-updated'))

android_delivery_failed = template.add_resource(
    sns.Topic('AndroidDevicesDeliveryFailedTopic',
              TopicName='devices-android-delivery-failed'))
