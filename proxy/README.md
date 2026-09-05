# proxy/

Apache reverse-proxy configuration. `sshomcat.conf.example` is a working vhost template (TLS
termination, static frontend serving, `/api` and `/ws` proxying to Tomcat on localhost).

Copy it to `sshomcat.conf`, fill in the real `ServerName` and certificate paths, and never
commit the real deployment config if it differs from the example in a way that matters for
security (it's already gitignored — see `../.gitignore`).
