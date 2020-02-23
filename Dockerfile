FROM openjdk:11.0.5-jre-stretch

ADD ["build/libs/dns-query-ns-1-all.jar", "settings.properties", "/"]

CMD java -jar dns-query-ns-1-all.jar