#! /usr/bin/env python3
import argparse
import os
import subprocess
import tempfile
from string import Template


def template(templateFileName, values):
    fp = tempfile.NamedTemporaryFile(mode = "w")

    template = Template(open(templateFileName).read())
    result = template.substitute(values)

    fp.write(result)

    fp.seek(0)
    return fp

def run(args):
    processes = []
    tempfiles = []

    env = os.environ.copy()
    if args.cyclone_network:
        parameters = {'networkInterface': args.cyclone_network}
        cyclone_dds_config = template('/workspace/templates/cyclonedds.xml.template', parameters)
        env['CYCLONEDDS_URI'] = f"file://{cyclone_dds_config.name}"

    optionalMapParameter = ""
    if args.map:
        optionalMapParameter = f" --args=\"--m={args.map}\""

    processes.append(subprocess.Popen("/entrypoint.sh ros2 daemon start", env=env, shell=True))
    processes.append(subprocess.Popen("/entrypoint.sh ros2 launch rosbridge_server rosbridge_websocket_launch.xml", env=env, shell=True))
    processes.append(subprocess.Popen(f"cd /workspace; /entrypoint.sh ./gradlew run{optionalMapParameter}", env=env, shell=True))

    for p in processes:
        p.wait()

    for file in tempfiles:
        file.close()

def get_args():
    parser = argparse.ArgumentParser(description='Dragonfly dashboard')

    parser.add_argument(
        '--cyclone_network',
        type=str,
        default=None)

    parser.add_argument(
        '--map',
        type=str,
        default=None)

    args = parser.parse_args()
    return args

def main():
    args = get_args()
    run(args)

if __name__ == '__main__':
    main()