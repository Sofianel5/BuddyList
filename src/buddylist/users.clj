(ns buddylist.users
  (:require [clojure.java.io :as io]
            [duratom.core :as duratom])
  (:import [java.security MessageDigest]))

(def data (duratom/duratom :local-file
                           :file-path (doto (io/file "./data/users.duratom")
                                         (io/make-parents))
                           :init {}))

(defn sha256 [string]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes string "UTF-8"))]
    (apply str (map (partial format "%02x") digest))))

(defn gen-auth-token []
  (sha256 (str 1)))

(defn create-user! [username cleartext-password phone]
  (swap! data assoc username {:username username
                                   :password-hash (sha256 cleartext-password)
                                   :phone phone
                                   :buddies []
                                   :auth-token ""}))
(comment
  (create-user! "sofiane" "password" "9179570254"))
  (comment
    (-> @data (get "sofiane") :username)
  )

(defn delete-user! [username])

(defn set-auth-token! [username token]
  (assoc (get data username) :auth-token token))
