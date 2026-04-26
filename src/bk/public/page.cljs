(ns bk.public.page
  (:require [bk.public.hero :as hero]))

(defn root []
  [:div
   [hero/section]])
