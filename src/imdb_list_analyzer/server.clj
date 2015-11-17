(ns imdb-list-analyzer.server
  (:gen-class)
  (:require [imdb-list-analyzer.core :as core]
            [imdb-list-analyzer.result-view :as resview]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.handler :refer [site api]]
            [compojure.route :refer [resources not-found files]]
            [ring.util.response :refer [response status resource-response]]
            [ring.middleware.multipart-params :as multiparams]))

(defn handle-hello-req []
  (do
    (println "hello req succes!")
    ;Response to server
    (str "Hello!")))

(defn handle-csv-req [request]
  (do
    (println "csv req success!")
    (println request)
    (println (core/one-file-analysis (:tempfile (:csv (:params request)))))
    ;Response to server
    (resview/jsonify-single-result
      (core/one-file-analysis (:tempfile (:csv (:params request)))))))

(defroutes site-routes
           (GET "/" [] (resource-response "index.html" {:root "public"}))
           (POST "/hello" [] (handle-hello-req))
           (POST "/analyze" req (handle-csv-req req))
           (resources "/")
           (not-found "Page not found"))

(def sites
  (site site-routes))

(def app
  (-> (routes sites)
      multiparams/wrap-multipart-params))
