# Event-Driven Microservices System

This is a event-driven microservices system for a simplified e-commerce flow (Orders -> Inventory -> Payments -> Notifications) built using Spring Boot and Kafka.
It demonstrates the use of event-driven architecture in a microservices environment.

![Event-Driven Microservices System](Event-Driven Microservices Architecture.png)

## Prerequisites


- Java 21 or higher
- Maven 3.6 or higher
- Docker and Docker Compose
- PostgreSQL 15 or higher
- Kafka 7.5 or higher

## How to run

1. Clone the repository
2. Build the project using Maven
3. Run the docker-compose.yml file
4. Start the services using the following command:
   ```
   docker-compose up
   ```
5. Access the services using the following URLs:
   >Create Order:
   >> http://localhost:8080/orders

   RequestBody
    ```
   {
   "customerId": "123e4567-e89b-12d3-a456-427714174000",
   "items": [{"sku":"monitor","qty":1,"price":2000.0},{"sku":"book","qty":2,"price":100.0}]
   }
   ```
   > 
   > Get Order:
   >> http://localhost:8080/orders/{orderId}
   