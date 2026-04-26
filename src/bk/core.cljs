(ns bk.core
  (:require [reagent.dom :as rdom]
            [bk.public.page :as page]))

(defn init []
  (rdom/render [page/root] (.getElementById js/document "app")))
