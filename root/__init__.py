from troposphere import Template

from root import groups
from root import roles
from root import robots
from root import artifacts

template = Template()

groups.generate(template)
roles.generate(template)
robots.generate(template)
artifacts.generate(template)
