FROM alpine:3.12

# Install dependencies
RUN apk add --no-cache \
    build-base \
    autoconf \
    automake \
    libtool \
    git

# Clone Twemproxy source code
RUN git clone https://github.com/twitter/twemproxy.git /twemproxy

# Build Twemproxy
WORKDIR /twemproxy
RUN autoreconf -fvi \
    && ./configure \
    && make \
    && make install

# Clean up
RUN apk del build-base autoconf automake libtool git \
    && rm -rf /var/cache/apk/* /twemproxy

# Add configuration file
COPY nutcracker.yaml /etc/nutcracker.yaml

# Expose port
EXPOSE 6379

# Start Twemproxy
ENTRYPOINT ["nutcracker", "-c", "/etc/nutcracker.yaml"]
