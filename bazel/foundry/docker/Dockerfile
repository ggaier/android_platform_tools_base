FROM gcr.io/cloud-marketplace/google/rbe-debian8@sha256:46c4fd30ed413f16a8be697833f7c07997c61997c0dceda651e9167068ca2cd3
RUN apt-get update && apt-get --no-install-recommends install -y ca-certificates-java locales && rm -rf /var/lib/apt/lists/*
# Install lib32ncurses5 for sdk linker dependency on 32 bit libraries.
RUN apt-get update && apt-get install -y lib32ncurses5 lib32z1
# Masks /dev/random and makes it a directory, when java tries to use it it reverts to a different mechanism
VOLUME /dev/random
# Other failed attempts:
#RUN rm /dev/random && ln /dev/urandom /dev/random
#RUN mount --bind /dev/urandom /dev/random
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
RUN echo en_US.UTF-8 UTF-8 >> /etc/locale.gen
RUN locale-gen en_US.UTF-8
