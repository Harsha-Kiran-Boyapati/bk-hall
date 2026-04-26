(ns bk.admin.state
  (:require [reagent.core :as r]))

(defonce session (r/atom nil))      ; Supabase session object or nil
(defonce role (r/atom nil))         ; "owner" | "staff" | nil
(defonce screen (r/atom :login))    ; current admin screen keyword
(defonce screen-params (r/atom {})) ; params for current screen (e.g. {:booking-id "..."})
