(ns buddylist.users
  (:require [clojure.java.io :as io]
            [duratom.core :as duratom])
  (:import org.mindrot.jbcrypt.BCrypt))

(def data (duratom/duratom :local-file
                           :file-path (doto (io/file "./data/users.duratom")
                                         (io/make-parents))
                           :init {}))

(defn hashpw [raw]
  (BCrypt/hashpw raw (BCrypt/gensalt 12)))

(defn checkpw [raw hashed]
  (boolean (BCrypt/checkpw raw hashed)))

(defn gen-auth-token []
  (.toString (java.util.UUID/randomUUID)))

(defn create-user! [username cleartext-password phone]
  (swap! data assoc username {:username username
                              :password-hash (hashpw cleartext-password)
                              :phone phone
                              :buddies []
                              :auth-token ""}))
(comment
  @data
  (reset! data {})
  (create-user! "sofiane" "password" "9179570254")
  (-> @data (get "sofiane") :username)
  (checkpw "password" (get-in @data ["sofiane" :password-hash]))
  (checkpw "bad" (get-in @data ["sofiane" :password-hash]))
  )

(defn delete-user! [username]
  ;; TODO: implement
  )

(defn set-auth-token! [username token]
  ;; TODO: swap the atom?
  (assoc (get data username) :auth-token token))
