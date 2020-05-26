# Sauron
Sauron is a Foscam compatible webcam daemon, written in Kotlin.

# Features
 - Multi-platform: Written in Kotlin, and thus compatible with any JVM platform
 - Flexible inputs: Can run any command to take the next frame.
   - Out of the box, supports Raspberry PI camera using `raspistill` command.
 - Support for standard webcam app integration (following e.g. the Foscam standard)
 - MJPEG support for easy integration into e.g. dashboards or into analysis frameworks.

# Installation
I'll provide way to create a Debian package at some point, but for now, here are the manual steps to get it built and installed:

## Build
First, build runnable JAR file:
```
./gradlew sauron
```

The result is a file like `sauron-0.1.jar`. This file can be copied anywhere and run:

```
java -jar sauron-0.1.jar
```

## Installation (Linux/Systemd)
The following are instructions for a Linux based system that has systemd support, especiall the Raspberry Pi:

First, create a directory to put sauron in, preferable under the main `pi` user's home directory:

```
mkdir -p /home/pi/sauron
```

Then copy the `sauron-0.1.jar` into it.

To support easy updates later, generate a symlink:
```
ln -s /home/pi/sauron/sauron-0.1.jar /home/pi/sauron/sauron.jar
```

With this in place, create the following systemd configuration as `/lib/systemd/system/sauron.service`:

```
[Unit]
Description=Sauron Service
After=multi-user.target

[Service]
Type=forking
ExecStart=screen -dmS sauron java -jar /home/pi/sauron/sauron.jar
User=pi

[Install]
WantedBy=multi-user.target
```

Make sure this file has the correct permissions:
```
sudo chmod 644 /lib/systemd/system/sauron.service
```

Note: This file will launch sauron inside a screen session, which is nice as it allows you to later ssh into the system and simply look at the running output using `screen -r sauron`.

Now activate the service:
```
sudo systemctl daemon-reload
sudo systemctl enable sample.service
```

Once you restart the system, Sauron should be running. To run it right away, run:
```
sudo systemctl start sauron.service
```
