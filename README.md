# pedestal-swagger

[![Build status](https://circleci.com/gh/frankiesardo/pedestal-swagger.svg?style=shield)](https://circleci.com/gh/frankiesardo/pedestal-swagger) [![Dependencies Status](http://jarkeeper.com/frankiesardo/pedestal-swagger/status.png)](http://jarkeeper.com/frankiesardo/pedestal-swagger)

Generate Swagger documentation from pedestal routes

- [Documentation](http://frankiesardo.github.io/pedestal-swagger/)

- [Demo](https://pedestal-swagger.herokuapp.com)

## Download

[![Clojars Project](http://clojars.org/frankiesardo/pedestal-swagger/latest-version.svg)](http://clojars.org/frankiesardo/pedestal-swagger)

## Usage

```clj
(:require [io.pedestal.interceptor.helpers :as interceptor]
          [schema.core :as schema]
          [pedestal.swagger.core :as swagger])
```

Annotate your interceptors using `swagger/annotate`. This function will attach swagger metadata to your interceptor.

```clj
(def my-endpoint
  (swagger/annotate
   {:summary "Enpoint for stuff"
    :description "This is an interesting endpoint"
    :parameters {:query {:param1 schema/Bool}
                 :body {:param2 schema/Str
                        :param3 schema/Inst}}
    :responses {:default {:headers {:x-tracking schema/Uuid}}
                418 {:description "I'm sorry, I'm a teapot"}}}
   (interceptor/handler
    ::my-endpoint
    (fn [request]
      ...))))
```

You can use these interceptors just like any other interceptors in your route definition.

```clj
(defroutes routes [[["/my-endpoint" {:get my-endpoint}]]])
```

And of course there are some helping macros in the swagger namespace to help you define them more succintely.

```clj
(swagger/defhandler my-handler
  {:summary ".."
   :parameters {..}
   :responses {..}}
  [request]
  ...)
```

A swagger path specification can be generated from your route table. You can inspect at any time what it looks like calling `pedestal.swagger.doc/gen-paths`.

```clj
(gen-paths routes)

;; => {"/my-endpoint" {:get {...}} ...}
```

But what you normally want is to inject the swagger paths in your route table, so that is available to your interceptors. There's a handy macro for that.

```clj
(swagger/defroutes routes
  {:title "Swagger API"
   :description "Swagger + Pedestal api"}
  [[["/my-endpoint" {:get my-endpoint}]]])
```

The second argument to defroutes is an optional map that will be merged with the generated paths and some default values to create a complete swagger documentation. The resulting swagger object looks like this:

```clj
{:swagger "2.0"
 :info {:title "Swagger API"
        :description "Swagger + Pedestal api"
        :version "0.0.1"}
 :paths {"/my-endpoint" {...}}}
```

A special handler can be added to convert this data structure in a json schema and serve it on a path (usually named `swagger.json`):

```clj
(swagger/defroutes routes
  [[["/my-endpoint" {:get my-endpoint}]
    ["/swagger.json" {:get [(swagger/swagger-json)]}]]])
```

And of course you can add a swagger-ui endpoint to provide an easy way to view and play with your API.

```clj
(swagger/defroutes routes
  [[["/my-endpoint" {:get my-endpoint}]
  ["/swagger/swagger.json" {:get [(swagger/swagger-json)]}]
  ["/swagger/*resource" {:get [(swagger/swagger-ui)]}]]])
```

_Note that the swagger-ui endpoint requires a_ `*resource` _splat parameter._

If your endpoint is a plain ring handler or a pedestal interceptor it will not be included in your documentation.

```clj
(defn a-hidden-endpoint [request]
 ...)
```

All this would be a little uninteresting if we weren't able to leverage one of Pedestal's most interesting features: sharing logic between endpoints via interceptors.

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
  [[["/" ^:interceptors [(swagger/body-params)
                         (swagger/coerce-request)
                         (swagger/validate-response)]
     ["/thing/:id" ^:interceptors [load-thing-from-db]
      {:get do-get-thing}
      {:put do-update-thing}
      {:delete do-delete-thing}]]]])
```

Note that you usually need to include (swagger/body-params) as it will make sure the keys in the request map are where the validator expect to find them. Each interceptor can still be configured to tweak is behaviour.

For a complete example have a look at the `sample` project.


## Roadmap

- Support multiple swagger.json and swagger-ui endpoints (e.g. for versioned apis)
- Content negotiation and documenting `consumes`
- More failures at compile time (tags not in sync, produces/consumes not in sync)

## License

Copyright © 2014 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
