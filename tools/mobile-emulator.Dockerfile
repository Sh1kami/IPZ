FROM panchyshkin-ipz-android-base:lab09
RUN sdkmanager 'emulator' 'system-images;android-28;default;x86_64'
RUN echo no | avdmanager create avd -n ipz_phone -k 'system-images;android-28;default;x86_64' --device 'pixel_2' --force
ENV PATH="/opt/android/emulator:$PATH"
EXPOSE 5555
CMD ["emulator", "-avd", "ipz_phone", "-no-window", "-no-audio", "-no-boot-anim", "-gpu", "swiftshader_indirect", "-accel", "off", "-memory", "1536", "-cores", "4", "-no-snapshot", "-no-metrics"]
