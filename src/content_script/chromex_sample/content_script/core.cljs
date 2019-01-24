(ns chromex-sample.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! timeout]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [oops.core :refer [oget oset! ocall oapply ocall! oapply!
                               oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]))
(defn has-positioned-parent [el]
  (if (#{"BODY" "SCRIPT" "HTML" "HEAD"} (.-tagName el))
    false
    (let [parent (.-parentNode el)
          parent-position (.-position (.getComputedStyle js/window parent))]
      (if (not= "static" parent-position)
        true
        (recur parent)))))

(defn is-positioned-child [el]
  (or (= "BODY"(oget el "?offsetParent.?tagName")) (has-positioned-parent el)))

(defn shift-header-bar-down [bar-height-str]
  (let [body (.-body js/document)
        children (.getElementsByTagName body "*")]
    (doseq [child (array-seq children) :when child]
      ;(log "child: " child)
      (let [styles (.getComputedStyle js/window child)
            position (.getPropertyValue styles "position")
            top (.getPropertyValue styles "top")
            bottom (.getPropertyValue styles "bottom")
            height (.getPropertyValue styles "height")]
        ;(log "position" position "top" top "bottom" bottom "height" height)
        (when (and (#{"fixed" "absolute" "sticky"} position) (not= "auto" top) (not (is-positioned-child child)))
          ;(log "About to move down child" (.-classList child))
          (doto child
            (.setAttribute "data-oldtop" top)
            (oset! "style.top" (str (+ (js/parseInt top) (js/parseInt bar-height-str)) "px"))))
        (when (and (not= "auto" height) (= "0px" top bottom))
          (doto child
            (.setAttribute "data-oldheight" height)
            (oset! "style.height" (str "calc(100% - " bar-height-str ")"))))))))

(defn add-ticker-bar []
      (do
        (log "CONTENT SCRIPT: about to add top-bar")
        (when (not (.getElementById js/document "plugin-ticker-bar"))
          (let [body (.-body js/document)
                bar-placeholder (doto (.createElement js/document "div")
                                  (.setAttribute "id" "plugin-ticker-bar-placeholder")
                                  (oset! "innerHTML" "placeholder")
                                  (oset! "style" "width: 100%; background: pink; height: 30px"))
                bar (doto (.createElement js/document "div")
                          (.setAttribute "id" "plugin-ticker-bar")
                          (oset! "innerHTML" "The Toolbar To Be")
                          (oset! "style" "position: fixed; top: 0px; width: 100%; background: pink; z-index: 99999; height: 30px"))]
            (do
              (shift-header-bar-down "30px")
              (.insertBefore body bar (.-firstChild body))
              (.insertBefore body bar-placeholder (.-firstChild body)))))
        (log "done adding bar")))

(defn remove-ticker-bar []
  (let [bar (.getElementById js/document "#plugin-ticker-bar")
        placeholder (.getElementById js/document "#plugin-ticker-bar-placeholder")
        top-modified-elements (.querySelectorAll js/document "[data-oldtop]")
        height-modified-elements (.querySelectorAll js/document "[data-oldheight]")]
    (do
      (.removeChild (.-parentNode bar) bar)
      (.removeChild (.-parentNode placeholder) placeholder)
      (doseq [el (array-seq top-modified-elements)]
        (oset! el "style.top" (.getAttribute el "data-oldtop")))
      (doseq [el (array-seq height-modified-elements)]
        (oset! el "style.height" (.getAttribute el "data-oldheight"))))))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [message]
  (log "CONTENT SCRIPT: got message:" message)
  (when (= "add-ticker" message)
    (add-ticker-bar))
  (when (= "remove-ticker" message)
    (remove-ticker-bar)))



(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "CONTENT SCRIPT: leaving message loop")))

; -- a simple page analysis  ------------------------------------------------------------------------------------------------

(defn do-page-analysis! [background-port]
  (let [script-elements (.getElementsByTagName js/document "script")
        script-count (.-length script-elements)
        title (.-title js/document)
        msg (str "CONTENT SCRIPT: document '" title "' contains " script-count " script tags.")]
    (log msg)
    (post-message! background-port msg)))



(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from CONTENT SCRIPT!")
    (run-message-loop! background-port)
    (do-page-analysis! background-port)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "CONTENT SCRIPT: init")
  (connect-to-background-page!))
