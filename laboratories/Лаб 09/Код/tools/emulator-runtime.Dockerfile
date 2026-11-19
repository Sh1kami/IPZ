FROM panchyshkin-ipz-appium:lab09
RUN apt-get update && apt-get install -y --no-install-recommends libx11-6 libxcb1 libxkbcommon0 libnss3 libpulse0 libasound2 libgl1 libxdamage1 libxfixes3 libxi6 libxrandr2 libxrender1 libxtst6 libegl1 libfontconfig1 && rm -rf /var/lib/apt/lists/*
COPY scripts/start_mobile.sh /usr/local/bin/start-mobile
RUN chmod +x /usr/local/bin/start-mobile
CMD ["/usr/local/bin/start-mobile"]
