@echo off
echo Starting MediCare Hospital System...
javac -cp "lib\mysql-connector-java.jar;src" src\*.java
java -cp "lib\mysql-connector-java.jar;src" Main
pause