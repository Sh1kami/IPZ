FROM node:22-bookworm-slim AS node
FROM panchyshkin-ipz-emulator:lab09
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm && ln -s ../lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
ENV APPIUM_HOME=/opt/appium
RUN npm install -g appium@3.1.0 && appium driver install uiautomator2@5.0.5
EXPOSE 4723
