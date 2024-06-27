FROM alpine:3.12

RUN apk add --no-cache \
    build-base \
    autoconf \
    automake \
    libtool \
    git

RUN git clone https://github.com/twitter/twemproxy.git /twemproxy

WORKDIR /twemproxy
RUN autoreconf -fvi \
    && ./configure \
    && make \
    && make install

RUN apk del build-base autoconf automake libtool git \
    && rm -rf /var/cache/apk/* /twemproxy

COPY nutcracker.yaml /etc/nutcracker.yaml

EXPOSE 6379

ENTRYPOINT ["nutcracker", "-c", "/etc/nutcracker.yaml"]
