## Resumen de la solución

### Stack

- Java 21, 
- Spring Boot 4.1.1
- Maven


### Decisiones

- Se usan hilos virtuales porque las consultas al catálogo son operaciones bloqueantes de entrada/salida y de esa forma se evita hacer uso de los hilos de la plataforma.
- Se aplica una política de "best effort" al obtener los detalles de tal forma que si falla alguno se devuelve una respuesta valida con los productos disponibles.
- Las llamadas de detalle son independientes y se procesan en paralelo para reducir la latencia.
- El cliente HTTP aplica los siguientes valores teniendo como referencia el test de carga que se incluia en la prueba pero se puede modificar en la configuración de la aplicación:

| Propiedad/configuración | Valor | Motivo |
| --- | --- | --- |
| `products.client.max-concurrency-per-request` | `10` | Limita las llamadas de detalle simultáneas por petición. |
| `products.client.connect-timeout` | `1s` | Evita esperas prolongadas al establecer una conexión. |
| `products.client.connection-request-timeout` | `1s` | Evita esperas prolongadas al conseguir una conexión del pool. |
| `products.client.read-timeout` | `7s` | Permite el caso lento de `5s` del mock y corta el de `50s`. |
| `products.client.max-connections` y `max-connections-per-route` | `600` | En esta prueba hay `200 VUs` y hasta `3` detalles por petición, de ahí el límite teórico de `600` llamadas simultáneas. En un entorno real habría que revisar el número habitual y máximo de IDs, el tráfico esperado y la capacidad del catálogo antes de fijar este valor. |
| Configuración de `HttpClient` | Reintentos desactivados | Evita duplicar tráfico cuando el catálogo ya está fallando. |

### Pruebas

Se ha dockerizado la aplicación, añadiendo al compose el servicio `app`. Para levantar la aplicación, los mocks y la infraestructura:

```bash
docker-compose up -d --build app simulado influxdb grafana
```

La API queda disponible en `http://localhost:5000/product/1/similar`.

Para ejecutar los tests automatizados:

```bash
mvn verify
```

# Backend dev technical test
We want to offer a new feature to our customers showing similar products to the one they are currently seeing. To do this we agreed with our front-end applications to create a new REST API operation that will provide them the product detail of the similar products for a given one. [Here](./similarProducts.yaml) is the contract we agreed.

We already have an endpoint that provides the product Ids similar for a given one. We also have another endpoint that returns the product detail by product Id. [Here](./existingApis.yaml) is the documentation of the existing APIs.

**Create a Spring boot application that exposes the agreed REST API on port 5000.**

![Diagram](./assets/diagram.jpg "Diagram")

Note that _Test_ and _Mocks_ components are given, you must only implement _yourApp_.

## Testing and Self-evaluation
You can run the same test we will put through your application. You just need to have docker installed.

First of all, you may need to enable file sharing for the `shared` folder on your docker dashboard -> settings -> resources -> file sharing.

Then you can start the mocks and other needed infrastructure with the following command.
```
docker-compose up -d simulado influxdb grafana
```
Check that mocks are working with a sample request to [http://localhost:3001/product/1/similarids](http://localhost:3001/product/1/similarids).

To execute the test run:
```
docker-compose run --rm k6 run scripts/test.js
```
Browse [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test) to view the results.

## Evaluation
The following topics will be considered:
- Code clarity and maintainability
- Performance
- Resilience
