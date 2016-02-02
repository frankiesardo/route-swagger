# route-swagger

[![Build status](https://circleci.com/gh/frankiesardo/route-swagger.svg?style=shield)](https://circleci.com/gh/frankiesardo/route-swagger)

Generate Swagger documentation from pedestal (or tripod) routes

- [Demo](https://pedestal-swagger.herokuapp.com)

## For old pedestal-swagger users

This project now focuses solely on route transformation and schema validation and thus is pedestal-agrostic.

Route-swagger is a much lower level library. Everything the old pedestal-swagger did is still possible (look at the example repo) but requires a bit more boilerplate. The major breaking difference is that now route-swagger uses ring specific keys for describing the schema rather than swagger ones, e.g.

```clj
{:parameters {:body-params ..
              :form-params ..
              :query-params ..
              :path-params ..
              :headers ..}
  :responses {500 {:body .. :headers ..}}}            
```

Instead of `body`, `formData`, `query`, `schema`, etc. That should make it much more user friendly for clojure users.

For a nicer integration with pedestal, extra features and easier migration path from the old pedestal-swagger check out [pedestal-api](https://github.com/oliyh/pedestal-api).

## Download

[![Clojars Project](http://clojars.org/frankiesardo/route-swagger/latest-version.svg)](http://clojars.org/frankiesardo/route-swagger)

## Usage

Have a look at the project under the example folder for a working pedestal app

## License

Copyright © 2015 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
