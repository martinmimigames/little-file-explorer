FROM debian:bookworm-slim AS builder

# need git, openjdk and android sdk
RUN apt-get update && apt-get install -y git openjdk-17-jdk-headless sdkmanager

# get required android sdk
RUN sdkmanager "build-tools;30.0.3" "platforms;android-33"

# accept licenses for android sdk
RUN yes | sdkmanager --licenses

WORKDIR /home

# create a debug keystore for signing apk
RUN keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# copy source code
COPY . /home/little-file-explorer

# enter project folder
WORKDIR /home/little-file-explorer

# create a keystore.properties file in the PARENT DIRECTORY of little-file-explorer
RUN echo "\
storePassword=android\n\
keyPassword=android\n\
keyAlias=androiddebugkey\n\
storeFile=/home/debug.keystore\
" > ../keystore.properties

# run the gradlew wrapper to build apk
RUN chmod 700 ./gradlew

RUN ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug --no-daemon --console=plain

FROM scratch AS output

COPY --from=builder /home/little-file-explorer/app/build/outputs/apk/debug/app-debug.apk .
