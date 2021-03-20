(ns buddylist.users
  (:require [clojure.java.io :as io]
            [duratom.core :as duratom])
  (:import org.mindrot.jbcrypt.BCrypt))

(def users (duratom/duratom :local-file
                           :file-path (doto (io/file "./data/users.duratom")
                                         (io/make-parents))
                           :init {}))

(def buddies (duratom/duratom :local-file
                            :file-path (doto (io/file "./data/buddies.duratom")
                                         (io/make-parents))
                            :init #{}))

(defn hashpw [raw]
  (BCrypt/hashpw raw (BCrypt/gensalt 12)))

(defn checkpw [raw hashed]
  (boolean (BCrypt/checkpw raw hashed)))

(defn gen-auth-token []
  (.toString (java.util.UUID/randomUUID)))

(defn create-user! [username cleartext-password phone]
  (swap! users assoc username {:username username
                              :password-hash (hashpw cleartext-password)
                              :phone phone
                              :buddies []
                              :auth-token nil}))

(defn delete-user! [username]
  (swap! users dissoc username))

;; TODO: might be nice to support a set of auth tokens so user can be logged in from
;; multiple clients.
(defn set-auth-token! [username token]
  (swap! users assoc-in [username :auth-token] token))

(defn create-buddies! [username-one username-two]
  (swap! buddies conj #{username-one username-two}))

(defn remove-buddies! [username-one username-two]
  (swap! buddies disj #{username-one username-two}))

(comment
  (set-auth-token! "sofiane" (gen-auth-token))
  users
  )
(comment
  (create-user! "sofiane" "password" "9179570254")
  (create-user! "liam" "password" "9179570254")
  (-> @users (get "sofiane") :username)
  (create-buddies! "liam" "sofiane")
  @buddies
  (remove-buddies! "liam" "sofiane")
  (delete-user! "sofiane")
  @users
)