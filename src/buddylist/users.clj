(ns buddylist.users
  (:require [clojure.java.io :as io]
            [duratom.core :as duratom]
            [clojure.string :as str]
            [java-time]
            [phone-number.core :as phone]
            [slingshot.slingshot :refer :all]
            [buddylist.forjure :refer [dissoc-by]]
            [buddylist.storage :as store])
  (:import org.mindrot.jbcrypt.BCrypt))

(def users (duratom/duratom :local-file
                            :file-path (doto (io/file "./data/users.duratom")
                                         (io/make-parents))
                            :init {}))

(def buddies (duratom/duratom :local-file
                              :file-path (doto (io/file "./data/buddies.duratom")
                                           (io/make-parents))
                              :init {})) ;; Map of #{user-a user-b}, <Messages>[]

(defn hashpw [raw]
  (BCrypt/hashpw raw (BCrypt/gensalt 12)))

(defn checkpw [raw hashed]
  (boolean (BCrypt/checkpw raw hashed)))

(defn gen-auth-token []
  (.toString (java.util.UUID/randomUUID)))

(defn validate-user [username cleartext-password phone email first-name last-name]
  (cond-> {:username [] :password [] :phone [] :email [] :first-name [] :last-name []}
    (empty? username) (update :username conj "Username must exist")
    (empty? cleartext-password) (update :password conj "Password must exist")
    (empty? phone) (update :phone conj "Must provide phone number")
    (empty? email) (update :email conj "Must provide email")
    (empty? first-name) (update :first-name conj "Must provide first name")
    (empty? last-name) (update :last-name conj "Must provide last name")
    (contains? @users username) (update :username conj "Username already registered")
    (contains? (set (map :phone (vals @users))) phone) (update :phone conj "Phone already registered")
    (contains? (set (map :email (vals @users))) email) (update :email conj "Email already registered")
    (or (> (count username) 15) (> 3 (count username))) (update :username conj "Username must be between 3 and 15 letters")
    (->> username (re-matches #"(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])") nil?) (update :username conj "Username can only contain letters, numbers, and _ or . but not twice in a row")
    (->> email (re-matches #"[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?") nil?) (update :email conj "Invalid email")
    (not (phone/valid? phone)) (update :phone conj "Invalid phone number")))

(defn is-user-valid [m]
  (every? empty? (vals m)))

(defn standardize-username [s] (-> s
                                   str
                                   str/lower-case
                                   str/trim))

(defn standardize-phone [s] (-> s
                                str
                                str/trim))

(defn standardize-name [s] (->> s
                                str
                                (#(str/split % #"\b"))
                                (map str/capitalize)
                                str/join))

;; returns new user map
;; TODO: Validate username, password, phone
(defn create-user! [username cleartext-password phone email first-name last-name]
  (let [username (standardize-username username)
        phone (standardize-phone phone)
        first-name (standardize-name first-name)
        last-name (standardize-name last-name)
        validation-res (validate-user username cleartext-password phone email first-name last-name)]
    (if (is-user-valid validation-res) (let [user {:username username
                                                   :first-name first-name
                                                   :last-name last-name
                                                   :email email
                                                   :password-hash (hashpw cleartext-password)
                                                   :phone phone
                                                   :buddies []
                                                   :auth-token (gen-auth-token)
                                                   :status nil
                                                   :profile-pic nil}]
                                         (swap! users assoc username user)
                                         user)
        ; Should probably throw
        (throw+ validation-res))))

(defn delete-user! [username]
  (swap! buddies dissoc-by #(contains? % username))
  (swap! users dissoc username))

(comment
  (def username "sofiane")
  
  (get-buddy-map "aaron")
  (-> @users (get "sofiane") :first-name)
  (do
    (create-buddies! "liam" "sofiane")
    (create-buddies! "liam" "aaron")
    (create-buddies! "aaron" "sofiane"))
  (create-user! "fake" "password" "+17187212721")
  (dissoc-by @buddies #(contains? % "fake"))
  (contains? #{"liam" "sofiane"} "fake")
  (delete-user! "fake")
  @buddies)

;; TODO: might be nice to support a set of auth tokens so user can be logged in from
;; multiple clients. 
;;(Question: is it bad if multiple clients to use the same auth token?)
(defn set-auth-token! [username token]
  (get (swap! users assoc-in [username :auth-token] token) username))

(defn get-auth-token [username password]
  (let [user (get @users username)
        auth (checkpw password (:password-hash user))]
    (if auth
      (if-not (:auth-token user)
        (set-auth-token! (:username user) (gen-auth-token))
        user)
      nil)))

;; TODO: Do nothing if buddies exist
; Should it be a list of vector?
(defn create-buddies! [username-one username-two]
  (if (contains? @buddies #{username-one username-two}) nil (do (swap! buddies assoc #{username-one username-two} [])
                                                                (swap! users update-in [username-one :buddies] conj username-two)
                                                                (swap! users update-in [username-two :buddies] conj username-one))))

(defn remove-buddies! [username-one username-two]
  (swap! buddies dissoc #{username-one username-two})
  (swap! users assoc-in [username-one :buddies] (apply vector (remove #(= % username-two) (-> @users (get username-one) :buddies))))
  (swap! users assoc-in [username-two :buddies] (apply vector (remove #(= % username-one) (-> @users (get username-two) :buddies)))))

(defn rearrange-buddies [username new-buddies-order]
  (-> (when (= (set (get-in @users [username :buddies])) (set new-buddies-order))
        (swap! users assoc-in [username :buddies] (apply vector new-buddies-order)))
      (get username)))

(rearrange-buddies "sofiane" ["aaron" "liam"])

(defn set-status! [username status]
  (get (swap! users assoc-in [username :status] status) username))

(defn send-message! [from to message]
  ;; Is there a difference here between assoc and assoc-in 
  (-> (swap! buddies assoc-in [#{from to}] (conj (get @buddies #{from to}) {:from from
                                                                            :to to
                                                                            :time (.toString (java-time/local-date-time))
                                                                            :id (-> @buddies
                                                                                    (get #{from to})
                                                                                    count
                                                                                    inc)
                                                                            :message message}))
      (get #{from to})
      last))

(defn get-buddy-map [buddy-name]
  (-> @users (get buddy-name) (dissoc :password-hash :auth-token :phone :buddies :email)))

(defn get-buddies [username]
  (->> (get-in @users [username :buddies]) (map get-buddy-map)))

(comment
  (create-buddies! "sofiane" "liam")
  (swap! users assoc-in ["aaron" :profile-pic])
  (let [username-one "sofiane"
        username-two "aaron"]
    (swap! users update-in [username-one :buddies] conj username-two)
    (swap! users assoc-in [username-one :buddies] (->> (get-in @users [username-one :buddies]) set (apply vector))))
  (get-in @users ["sofiane" :buddies])
  )

(defn get-convo [username buddy start offset]
  (let [full-convo (-> @buddies (get #{username buddy}))
        end-idx (- (count full-convo) 1)]
    (cond
      ; Empty
      (< end-idx 0) full-convo
      ; Fully contained
      (and (>= (- end-idx start offset) 0) (>= end-idx (- end-idx start))) (subvec full-convo (- end-idx start offset) (- end-idx start))
      ; Exceed to the left
      (and (> 0 (- end-idx start offset)) (>= end-idx (- end-idx start))) (subvec full-convo 0 (- (count full-convo) start))
      ; Exceed to the right
      (and (>= (- end-idx start offset) 0) (> (- end-idx start) end-idx)) (subvec full-convo (- end-idx start offset))
      ; Exceed on both sides
      :else full-convo)))

(defn get-recent-messages [username buddy n]
  (let [full-convo (-> @buddies (get #{username buddy}))]
    (if (> n (- (count full-convo) 1))
      full-convo
      (subvec full-convo (- (count full-convo) n)))))

(comment
  (def username "sofiane")
  (get @users "sofiane")
  (let [messages (-> @buddies (get #{"sofiane" "liam"}))]
    (->> messages (partition-by :from)
        (map (comp vec #(map :from %)))
        ))
  (-> @buddies (get #{"sofiane" nil}))
  (get @buddies #{"liam" "aaron"})
  (count (get-convo "liam" "" 0 25))
  (->> @buddies keys (filter #(contains? % username)) (map (fn [pair] (first (filter #(not= % username) pair)))) (map get-buddy-map))
  (get-buddies "sofiane")
  (remove-buddies! "sofiane" "test000")
  (get-recent-messages "sofiane" "aaron" 25)
  (keys @buddies)
  (def fakebuddies '(#{"liam" "sofiane"} #{"sofiane" "fake1"} #{"sofiane" "fake2"}))
  (->> fakebuddies (filter #(contains? % username)) (map (fn [pair] (first (filter #(not= % username) pair)))))
  (filter #(contains? % username) (keys @buddies))
  (get-buddy-map username)
  (send-message! "sofiane" "liam" "test from sofiane 2")
  (set-status! "liam" "something new")
  (count (get-convo "sofiane" "liam" 1 26))
  (get-buddies "sofiane"))

(defn authenticate-user [username auth-token]
  (let [assoc-user (get @users username)]
    (if (= auth-token (:auth-token assoc-user)) assoc-user nil)))

(comment
  (set-auth-token! "sofiane" (gen-auth-token))
  users
  (.toString (java-time/local-date-time)))

(comment
  (create-user! "sofiane" "password" "9179570254")
  (def user (authenticate-user "sofiane" "password"))
  @users
  user
  (set-auth-token! (:username user) (gen-auth-token))
  user
  (create-user! "fake" "password" "+17187212721")
  (-> @users (get "sofiane") :auth-token)
  (if (authenticate-user "sofiane" "9509c9ac-5bed-4597-8a56-54d262fa8457") true false)
  (create-buddies! "liam" "sofiane")
  @buddies
  (count (get @buddies #{"sofiane" "liam"}))
  (remove-buddies! "liam" "sofiane")
  (delete-user! "fake")
  (delete-user! nil)
  (set-status! "sofiane" "Coding BuddyList")
  (send-message! "sofiane" "liam" "Did u finish beatstreet?")
  (send-message! "liam" "sofiane" "Yes! Do you want to see?")
  @users)

(defn set-profile-picture [username f]
  (let [filename (str (gen-auth-token) ".png")
        url (store/upload-bytes filename f)]
    (-> (swap! users assoc-in [username :profile-pic] url)
        (get username))))
