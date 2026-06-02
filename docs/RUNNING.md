# Commands to run app from bash command line assuming the default install.

> The following are commands and processes that are good to know and come up fairly
> frequently, things like logging, starting the backend, starting the frontend, log
> tailing, where the creds are set, port busy alerts, querying db from bash,
> querying database from docker container, etc.  It's meant to evolve over time.
> Basically, this is just a shortened 'DEPLOYING-LOCAL.md'. 
---

## Running the app
### Steps:
- First bring up docker and start the pollsystem-db and mailpit containers.
- Go to /backend dir and run 'SPRING_PROFILES_ACTIVE=local ./gradlew bootRun'.
- cd to /frontend and run: npm run dev
- If you get a port :xxxx in use'  then do 'sudo lsof -t -i:3000' and run 'sudo kill -9 xxxxx' replacing 'xxxxx' with the process id from the lsof command.

## Accessing/Querying the DB:
### From the bash command line:
- Check if db is running 'docker ps --filter ancestor=postgres:16'
- From root of project: 'docker exec -it pollsystem-db psql -U polladmin -d pollsystem -
c "SELECT 1;"'
### Query db from docker desktop container:
- Select 'View details' in the container's menu.
- Click on the 'Exec' tab.
- psql -U polladmin -d pollsystem [RET] // enter polladmin password when prompted.
### Where db and other creds are located:
- /docker-compose.yml

