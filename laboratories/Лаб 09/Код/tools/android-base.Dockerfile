FROM ruby:3.3-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless unzip curl && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android ANDROID_SDK_ROOT=/opt/android JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="/opt/gradle/bin:/opt/android/cmdline-tools/latest/bin:/opt/android/platform-tools:$PATH"
RUN curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip -o /tmp/gradle.zip && unzip -q /tmp/gradle.zip -d /opt && mv /opt/gradle-8.9 /opt/gradle && rm /tmp/gradle.zip
RUN mkdir -p /opt/android/cmdline-tools && curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/sdk.zip && unzip -q /tmp/sdk.zip -d /tmp/sdk && mv /tmp/sdk/cmdline-tools /opt/android/cmdline-tools/latest && rm -rf /tmp/sdk /tmp/sdk.zip
RUN yes | sdkmanager --licenses > /dev/null
RUN sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
