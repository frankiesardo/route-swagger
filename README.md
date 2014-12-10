# pedestal-swagger

[![Build Status](https://secure.travis-ci.org/frankiesardo/pedestal-swagger.png)](http://travis-ci.org/frankiesardo/pedestal-swagger) [![Dependencies Status](http://jarkeeper.com/frankiesardo/pedestal-swagger/status.png)](http://jarkeeper.com/frankiesardo/pedestal-swagger)


Generate Swagger documentation from pedestal routes

## Download

[![Clojars Project](http://clojars.org/pedestal-swagger/latest-version.svg)](http://clojars.org/pedestal-swagger)

## Usage

```clojure
(:require [schema.core :as schema]
          [pedestal.swagger.core :as swagger])
```


Annotate your endpoints using `swagger/defhandler`. This macro takes a documentation map that will be attached to the interceptor meta.

```clojure
(swagger/defhandler my-endpoint
  {:summary "Enpoint for stuff"
   :description "This is an interesting endpoint"
   :parameters {:query {:override schema/Bool}
                :body {:name schema/Str
                       :date schema/Inst}}
   :responses {:default {:headers ["Location"]}
               418 {:description "I'm sorry, I'm a teapot"}}}
  [request]
  ...)
```

You can use these interceptors just like any other interceptors in your route definition.

```clojure
(defroutes routes [[["/my-endpoint" {:get my-endpoint}]]])
```

The generated swagger documentation for your endpoints is held in a swagger-object interceptor, so you need to add this interceptor to your route table before being able to inspect it. The `swagger/swagger-object` `interceptor-fn` accepts a map with your global api documentation infos.

```clojure
(def doc-spec
  {:title "Amazing app"
   :description "Pedestal + Swagger FTW!"
   ...})

(defroutes routes [[["/my-endpoint" {:get my-endpoint}
                     "/docs" {:get [(swagger/swagger-object doc-spec)]}]]])
```
You can have a quick peek at what your documentation will look like when served under the "/docs" endpoint.

```clojure
(-> routes
    pedestal.swagger.doc/inject-docs
    pedestal.swagger.doc/swagger-object)
; => {:swagger "2.0"
      :info {:title "Amazing app"
             :description "Pedestal + Swagger FTW!"}
      :paths {"/my-endpoint" [...]}}
```

And of course you can add a swagger-ui endpoint to provide easy to access and easy to experiment access to your api.

```clojure
(defroutes routes [[["/my-endpoint" {:get my-endpoint}
                     "/my-docs" {:get [(swagger/swagger-object doc-spec)]}
                     "/ui/*resource" {:get [(swagger/swagger-ui)]}]]])
```

Note that `swagger-ui` needs to have a route path with splat argument called `*resource` (because we will be serving static resources here).

All this would be a little uninteresting if we weren't able to leverage one of pedestal's most interesting features: sharing logic between endpoints via interceptors.

A common pattern is to have an interceptor at the root of a path that loads a resource.

```clojure
(swagger/defon-request load-thing-from-db
  {:parameters {:path {:id schema/Int}}
   :responses {404 {:description "Couldn't find the thing in the db"}}}
   [request]
    ...)

(swagger/defroutes routes
  [[["/thing/:id" ^:interceptors [load-thing-from-db]
      {:get do-get-thing}
      {:put do-update-thing}
      {:delete do-delete-thing}]]])
```
All the documentation speficied in the interceptor (such as parameters, reponses, description) will be inherited by the endpoints on the same path. Thus you can reuse both behaviour and the documentation for said behaviour.

And finally we want to be able to use the schemas in the documentation to check the incoming parameters or the outgoing responses. To do so we can include `swagger/coerce-parameters` and `swagger/validate-response` at the top of our route spec. The default behaviour of these interceptors could be overridden passing a custom coercion or validation function.

```clojure
(swagger/defroutes routes
  [[["/" ^:interceptors [(swagger/coerce-params) (swagger/validate-response)]
      ["/thing/:id" ^:interceptors [load-thing-from-db]
        {:get do-get-thing}
        {:put do-update-thing}
        {:delete do-delete-thing}]]]])
```

For a complete example have a look at the `sample` project.


## License

Copyright © 2014 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
