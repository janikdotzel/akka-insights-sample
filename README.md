# Akka Insights Sample

## Setup 

The Akka dependencies are available from Akka’s secure library repository. 
To access them you need to use a secure, tokenized URL as specified at https://account.akka.io/token.

The resolver need to be added to the `akka.sbt` and `plugins.sbt` before you start this project.

## Start OpenTelemetry developer sandbox

To run the OpenTelemetry Docker developer sandbox, first change into the unzipped directory at a terminal:
```
cd cinnamon-opentelemetry-docker-sandbox-2.22.0
```

Make sure that Docker is running and then start the developer sandbox using Docker Compose:
```
docker compose up
```

The developer sandbox version of Grafana is now available at http://localhost:3000.


## Start the Akka application

1. Start a local PostgreSQL server on default port 5432. The included `docker-compose.yml` starts everything required for running locally.
    ```shell
    docker compose up -d

    # creates the tables needed for Akka Persistence
    # as well as the offset store table for Akka Projection
    docker exec -i postgres-db psql -U shopping-cart -t < ddl-scripts/create_tables.sql
    ```

2. Start a first node:
    ```
    sbt -Dconfig.resource=local1.conf run
    ```

3. Try it with [grpcurl](https://github.com/fullstorydev/grpcurl):
    ```shell
    # add item to cart
    grpcurl -d '{"cartId":"cart2", "itemId":"socks", "quantity":3}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
   
    # get cart
    grpcurl -d '{"cartId":"cart2"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.GetCart
   
    # update quantity of item
    grpcurl -d '{"cartId":"cart2", "itemId":"socks", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.UpdateItem
   
    # check out cart
    grpcurl -d '{"cartId":"cart2"}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.Checkout
    ```
