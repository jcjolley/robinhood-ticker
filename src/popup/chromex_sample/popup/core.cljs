(ns ^:figwheel-always chromex-sample.popup.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [chromex.ext.tabs :as tabs]
            [reagent.core :as r]))

; -- a message loop ---------------------------------------------------------------------------------------------------------
(def bg-port (atom))
(defn process-message! [message]
  (log "POPUP: got message:" message))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (reset! bg-port background-port)
    (post-message! background-port "hello from POPUP!")
    (run-message-loop! background-port)))

(defn root []
  [:div
    [:button {:on-click #(post-message! @bg-port "add-ticker")}"Click to Add Ticker"]])

(defn mount-root []
  (do
    (r/render [root] (js/document.getElementById "popup-main"))
    (print "Mount-root was called")))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "POPUP: init")
  (mount-root)
  (connect-to-background-page!))
