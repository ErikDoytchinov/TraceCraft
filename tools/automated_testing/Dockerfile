FROM 3arthqu4ke/headlessmc:latest

# install screen so we can send commands later
RUN apt-get update \
 && apt-get install -y screen \
 && rm -rf /var/lib/apt/lists/*

# 1) copy your config + specifics mod in
COPY tools/automated_testing/bot0/config/config.properties \
     /headlessmc/HeadlessMC/config.properties
COPY tools/automated_testing/bot0/run/mods/hmc-specifics-1.21.5-2.2.1-lexforge-release.jar \
     /headlessmc/HeadlessMC/run/mods/

WORKDIR /headlessmc

# 2) download vanilla + forge
RUN hmc download 1.21.5 \
 && hmc forge 1.21.5

# 3) pre-cache everything by launching (in build!) for 1 minutes, then kill
#    this populates .minecraft/libraries so runtime containers don’t hit timeouts
RUN timeout 60 hmc launch 1 -lwjgl --background || true

# 4) at runtime, launch in a detached screen session and keep the container alive
ENTRYPOINT ["sh","-c","screen -dmS hmc hmc launch 1 -lwjgl --jvm -Xmx512M && tail -f /dev/null"]
