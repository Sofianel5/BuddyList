(ns buddylist.tray
  (:import [java.awt Taskbar Toolkit])
  (:require [clojure.java.io :as io]))

(Taskbar/isTaskbarSupported)

  (let [t (Taskbar/getTaskbar)]
    (.setIconImage t (.getImage (Toolkit/getDefaultToolkit)
                   (io/resource "buddylist/IconTemplate.png"))))
