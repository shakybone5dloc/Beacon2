# The page printer's egress proxy: Squid, refusing every address that isn't the public
# internet. See squid.conf.

FROM alpine:3.22
RUN apk add --no-cache squid
COPY squid.conf /etc/squid/squid.conf
USER squid
EXPOSE 3128
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD nc -z 127.0.0.1 3128 || exit 1
ENTRYPOINT ["squid", "-N", "-f", "/etc/squid/squid.conf"]
