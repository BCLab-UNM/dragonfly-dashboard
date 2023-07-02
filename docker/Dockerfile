FROM ros:galactic-ros-base

ENV NVIDIA_VISIBLE_DEVICES \
    ${NVIDIA_VISIBLE_DEVICES:-all}
ENV NVIDIA_DRIVER_CAPABILITIES \
    ${NVIDIA_DRIVER_CAPABILITIES:+$NVIDIA_DRIVER_CAPABILITIES,}graphics

ENV DEBIAN_FRONTEND noninteractive
ENV WORKSPACE /workspace
ENV MAVEN_VERSION 3.9.2

# Install dependencies
RUN apt-get -qq update
RUN apt-get -qq upgrade
RUN apt-get --fix-missing update && sudo apt-get install -f
RUN apt-get -qq install apt-utils build-essential vim
RUN apt-get -qq install software-properties-common

# Setup Java JDK package repository
RUN add-apt-repository ppa:linuxuprising/java

RUN echo debconf shared/accepted-oracle-license-v1-3 select true | sudo debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-3 seen true | sudo debconf-set-selections

# Install Mavros, Java & ROS Bridge
RUN apt-get -qq -y install ros-galactic-mavros oracle-java17-installer oracle-java17-set-default openjfx unzip ros-galactic-rosbridge-server libcanberra-gtk-module

# Install maven
RUN wget https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz -P /tmp
RUN tar xf /tmp/apache-maven-${MAVEN_VERSION}-bin.tar.gz -C /opt
RUN ln -s /opt/apache-maven-$MAVEN_VERSION /opt/maven

RUN export JAVA_HOME
ENV M2_HOME /opt/maven
ENV MAVEN_HOME /opt/maven
ENV PATH "$M2_HOME/bin:$PATH"

RUN rm /bin/sh && ln -s /bin/bash /bin/sh

# Clear apt cache to save on space
RUN rm -rf /var/lib/apt/lists/*

RUN echo "Xft.dpi: 192" > ~/.Xdefaults

# Setup Gradle - hoping this aids performance
RUN mkdir -p $WORKSPACE/gradle/wrapper
ADD build.gradle $WORKSPACE
ADD gradlew $WORKSPACE
ADD gradle/wrapper/* $WORKSPACE/gradle/wrapper/
RUN cd $WORKSPACE && \
	./gradlew clean

# Build and install java_rosbridge submodule
COPY java_rosbridge $WORKSPACE/java_rosbridge
RUN cd $WORKSPACE/java_rosbridge && \
    mvn clean install

# Install dragonfly infrastructure
COPY dragonfly $WORKSPACE/dragonfly
RUN source /opt/ros/galactic/setup.bash && \
	cd $WORKSPACE/dragonfly && \
	colcon build

# Install dragonfly desktop
COPY . $WORKSPACE
RUN cd $WORKSPACE && \
	./gradlew build --parallel

# Setup Entrypoint
COPY ./docker/entrypoint.sh /
COPY ./docker/entrypoint.py /

ENTRYPOINT ["/entrypoint.py"]
