# Dev-only Apache image for docker-compose.dev.yml. Enables the modules
# proxy/sshomcat.conf.example needs (they ship commented-out in the stock httpd:2.4 image) and
# includes our vhost.

FROM httpd:2.4

RUN sed -i \
    -e 's/^#\(LoadModule ssl_module .*\)/\1/' \
    -e 's/^#\(LoadModule socache_shmcb_module .*\)/\1/' \
    -e 's/^#\(LoadModule proxy_module .*\)/\1/' \
    -e 's/^#\(LoadModule proxy_http_module .*\)/\1/' \
    -e 's/^#\(LoadModule proxy_wstunnel_module .*\)/\1/' \
    -e 's/^#\(LoadModule headers_module .*\)/\1/' \
    conf/httpd.conf \
  && printf '\nInclude conf/extra/sshomcat.conf\n' >> conf/httpd.conf

COPY sshomcat.conf /usr/local/apache2/conf/extra/sshomcat.conf
COPY certs/sshomcat.crt certs/sshomcat.key /usr/local/apache2/certs/
