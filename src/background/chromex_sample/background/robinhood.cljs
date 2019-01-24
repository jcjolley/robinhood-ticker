(ns chromex-sample.background.robinhood
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def state (atom {:request-params {:headers {"authority" "api.robinhood.com"
                                             "content-type" "application/json"
                                             "accept" "*/*"}}
                  :form-params {:expires_in 86400
                                :grant_type "password"
                                :scope "internal"
                                :client_id "c82SH0WZOsabOXGP2sxqcj34FxkvfnWRZBKlBjFS"}}))

#_(defn- sanitize
    "Replaces spaces and underscores with hyphens"
    [response]
    (-> response
        (clojure.string/replace #"_| |:" {" " "-" "_" "-" ":" ""})
        clojure.string/lower-case))

#_(defn- prepare-keys [key]
    (->> key sanitize keyword))

(defn- response->body
  [response]
  (if (#{200 201} (:status response))
    (js->clj (.parse js/JSON (:body response)) :keywordize-keys true)
    nil))

(defn- build-get-config
  ([] {:request-params @state})
  ([query-params] (assoc (:request-params @state) :query-params query-params)))

(defn- build-post-config
  ([] (build-post-config nil))
  ([form-params] (update @state :form-params #(merge % form-params))))

(defn- extract-token! [credentials]
  (do
    (swap! state assoc :credentials credentials)
    (->> credentials
         ((juxt :token_type :access_token))
         (clojure.string/join " ")
         (swap! state assoc-in [:request-params :headers "authorization"]))))

#_(defn get
    ([path]
     (get path nil))
    ([path query-params]
     (try
       (->> (build-get-config query-params)
            (clj-http.client/get (str "https://api.robinhood.com/" path))
            response->body)
       (catch Exception e (str "Exception: " e)))))

#_(get "midlands/movers" {"direction" "up"})
#_(get "quotes/" {"symbols" "SIRI,AAPL"})

(defn get
  ([path]
   (get path nil))
  ([path query-params]
   (go (try (let [response (<! (->> (build-get-config query-params)
                                    (http/get (str "https://api.robinhood.com/" path))))]
              (swap! state assoc :responses (conj (:responses @state) (:body response)))
              (prn (:status response))
              (prn (:body response))
              (:body response))
         (catch :default e (prn e))))))

(defn post
  ([path]
   (post path nil))
  ([path form-params]
   (go (let [response (<! (->> (build-post-config form-params)
                               (http/post (str "https://api.robinhood.com/" path))))]

          (swap! state assoc :responses (conj (:responses @state) (:body response)))
          (:body response)))))


(defn login [username password]
  (go (let [result (<! (post "oauth2/token/" {:username username :password password}))]
       (extract-token! result))))

(defn calc-stock-info [[ticker close last-trade]]
  (let [lt (js/parseFloat last-trade)
        cl (js/parseFloat close)
        abs-change (.toFixed (- lt cl) 2)
        perc-change (str (.toFixed (* 100 (/ abs-change cl)) 2) "%")]
    {:ticker ticker :last-trade (.toFixed lt 2) :abs-change abs-change :perc-change perc-change}))

(defn get-stocks [stocks]
    (go (->> (get "quotes/" {"symbols" (clojure.string/join "," stocks)})
             (<!)
             :results
             (map (juxt :symbol :adjusted_previous_close :last_trade_price))
             (map calc-stock-info)
             (prn))))

(defn get-portfolio-value []
  (go (let [account (->> (get "accounts")
                         (<!)
                         :results
                         first
                         :account_number)
            value (->> (get (str "portfolios/" account "/"))
                       (<!)
                       (juxt :equity_previous_close :equity))])))
