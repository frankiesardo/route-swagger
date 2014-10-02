# pedestal-swagger

[![Build Status](https://secure.travis-ci.org/frankiesardo/pedestal-swagger.png)](http://travis-ci.org/frankiesardo/pedestal-swagger)

Generate Swagger documentation from pedestal routes

## Download

[![Clojars Project](http://clojars.org/pedestal-swagger/latest-version.svg)](http://clojars.org/pedestal-swagger)

## Usage

```clojure
(:require [schema.core :as schema]
          [pedestal.swagger.core :as swagger]
          [pedestal.swagger.doc :as doc]) ;; optional
```


Annotate your endpoints using `swagger/defendpoint`. This macro takes a documentation map that will be attached to the interceptor meta.

```clojure
(swagger/defendpoint my-endpoint
  {:summary "Enpoint for stuff"
   :description "This is an interesting endpoint"
   :params {:header schema/MyHeaderRequiredKeys
            :query schema/MyQueryModel}
   :responses {:default schema/MyResponseModel
               418 {:description "I'm sorry, I'm a teapot"}}}
  [request]
  ...)
```

You can use these interceptor just like any other interceptors in your route definition.

```clojure
(defroutes routes [[["/" {:get my-endpoint}]]])
```


And using `doc/generate-docs` you can create a valid swagger-object map.

```clojure
(doc/generate-docs routes)
; => {:swaggerVersion "2.0"
      :operations {...}}
```

But of course most of the time we want to provide an endpoint that returns the swagger documentation and possibly and enpoint to show the swagger ui. To do that we swap pedestal's `defroutes` with `swagger/defroutes`, which will first internally generate the docs so that we could serve them on an endpoint. We will use the special interceptors `swagger/swagger-object` and `swagger/swagger-ui` for the job.

```clojure
(def doc-spec
  {:title "Amazing app"
   :apiVersion "9.9.9"
   ...})

(swagger/defroutes routes
  [[["/" {:get my-endpoint}]
    ["/docs" {:get [(swagger/swagger-object doc-spec)]}]
    ["/ui/*resource" {:get swagger/swagger-ui}]]])
```
Note that `swagger-object` takes as a param an additional map where you can specify global documentation regarding your app and that `swagger-ui` needs to have a route path with splat argument called `*resource` (because we will be serving static resources here).

All this would be a little uninteresting if we weren't able to leverage one of pedestal's most interesting features: sharing logic between endpoints via interceptors.

A common pattern is to have an interceptor at the root of a path that loads a resource.

```clojure
(swagger/defon-request load-thing-from-db
  {:params {:path {:id schema/Int}}
   :responses {404 {:description "Couldn't find the thing in the db"}}}
   [request]
    ...)

(swagger/defroutes routes
  [[["/thing/:id" ^:interceptors [load-thing-from-db]
      {:get do-get-thing}
      {:put do-update-thing}
      {:delete do-delete-thing}]
    ["/docs" {:get [(swagger/swagger-object doc-spec)]}]
    ["/ui/*resource" {:get swagger/swagger-ui}]]])
```
All the documentation speficied in the interceptor (such as parameters, reponses, description) will be inherited by the endpoints on the same path. Thus you can reuse both behaviour and the documentation for said behaviour.

And finally we want to be able to use the schemas in the documentation to type-check the incoming parameters or the outgoing responses. To do so we can easily include `swagger/coerce-parameters` and `swagger/validate-response` at the top of our route spec. The default behaviour of these interceptors could be overridden passing a custom coercion/validation function.

```clojure
(swagger/defroutes routes
  [[["/" ^:interceptors [(swagger/coerce-params) (swagger/validate-response)]
      ["/thing/:id" ^:interceptors [load-thing-from-db]
        {:get do-get-thing}
        {:put do-update-thing}
        {:delete do-delete-thing}]
      ["/docs" {:get [(swagger/swagger-object doc-spec)]}]
      ["/ui/*resource" {:get swagger/swagger-ui}]]]])
```

For a complete example have a look at the `sample` project.


## License

Copyright © 2014 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
