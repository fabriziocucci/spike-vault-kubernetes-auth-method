FROM openjdk:8u151-jre-alpine
RUN mkdir /opt && mkdir /opt/app
COPY build/libs/*.jar /opt/app/spike-spring-cloud-vault.jar
CMD ["java", "-jar", "/opt/app/spike-spring-cloud-vault.jar"]