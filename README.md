# pedestal-swagger

[![Build Status](https://secure.travis-ci.org/frankiesardo/pedestal-swagger.png)](http://travis-ci.org/frankiesardo/pedestal-swagger) [![Dependencies Status](http://jarkeeper.com/frankiesardo/pedestal-swagger/status.png)](http://jarkeeper.com/frankiesardo/pedestal-swagger)


Generate Swagger documentation from pedestal routes

## Download

[![Clojars Project](http://clojars.org/pedestal-swagger/latest-version.svg)](http://clojars.org/pedestal-swagger)

## Usage

```clj
(:require [schema.core :as schema]
          [pedestal.swagger.core :as swagger])
```


Annotate your endpoints using `swagger/defhandler`. This macro takes a documentation map that will be attached to the interceptor meta.

```clj
(swagger/defhandler my-endpoint
  {:summary "Enpoint for stuff"
   :description "This is an interesting endpoint"
   :parameters {:query {:param1 schema/Bool}
                :body {:param2 schema/Str
                       :param3 schema/Inst}}
   :responses {:default {:headers ["Location"]}
               418 {:description "I'm sorry, I'm a teapot"}}}
  [request]
  ...)
```

You can use these interceptors just like any other interceptors in your route definition.

```clj
(defroutes routes [[["/my-endpoint" {:get my-endpoint}]]])
```

It's possible to generate the swagger paths documentation on the fly calling:

```clj
(pedestal.swagger.doc/generate-docs {:title "My App} routes)
;; => {:swagger "2.0"
;;     :title "My App"
;;     :paths {"/my-endpoint" {:get {...}}}}
```

But what you normally want is to inject the documentation in your route table, so that is available to your interceptors. There's a handy macro for that:

```clj
(swagger/defroutes routes
  {:title "My App"
   :version "0.1.0"}
  [[["/my-endpoint" {:get my-endpoint}]]])
```
The second argument to defroutes is a map containing general informations about your API that will be merged to the paths doc. A special endpoint can be added to serve the generated swagger documentation.

```clj
(swagger/defroutes routes
  [[["/my-endpoint" {:get my-endpoint}]
    ["/doc" {:get [(swagger/swagger-doc)]}]]])
```

And of course you can add a swagger-ui endpoint to provide easy to access and easy to experiment access to your api.

```clj
(swagger/defroutes routes
  [[["/my-endpoint" {:get my-endpoint}]
  ["/doc" {:get [(swagger/swagger-doc)]}]
  ["/ui/*resource" {:get [(swagger/swagger-ui)]}]]])
```

Note that the swagger-ui endpoint requires a `*resource` splat parameter.

All this would be a little uninteresting if we weren't able to leverage one of pedestal's most interesting features: sharing logic between endpoints via interceptors.

A common pattern is to have an interceptor at the root of a path that loads a resource.

```clj
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
All the documentation specified in the interceptor (such as parameters, responses, description) will be inherited by the endpoints on the same path. Thus you can reuse both behaviour and the documentation for said behaviour.

And finally we want to be able to use the schemas in the documentation to check the incoming parameters or the outgoing responses. To do so we can include `swagger/coerce-parameters` and `swagger/validate-response` at the top of our route spec. The default behaviour of these interceptors could be overridden passing a custom coercion or validation function.

```clj
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
