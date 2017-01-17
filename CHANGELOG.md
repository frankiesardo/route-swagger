## 0.4.5
- Bump to pedestal 0.5.2

## 0.4.4
- [Breaking] Move error handling to `pedestal.swagger.error`
- [Breaking] Coerce/validate functions do not have access to context anymore.
- Coerce/validate functions accept custom coercions as parameters.

## 0.4.3
- [Breaking] swagger-doc -> swagger-json
- [Breaking] no more keywordize-params
- [Breaking] coerce-params -> coerce-request
- Interceptor core/body-params now takes a parser map, ensures the body keys are consistent to the ones expected by schema/coerce-request

## 0.4.2
- Fix formData coercion
- Add `annotate` fn to attach metadata to interceptors

## 0.4.0
- Bump pedestal to 0.4.0
- Map parameter passed to defroutes is deep merged to the generated swagger object.
- As a consequence `doc-routes` is properly renamed to `gen-paths`.

## 0.3.0
- Initial version
