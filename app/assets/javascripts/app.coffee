
require [
  'angular',
  './services',
  './directives',
  './controllers',
  'angular-route'
],
  (angular) ->

    angular.module('AuctionHouse', ['AuctionHouse.services', 'AuctionHouse.directives', 'AuctionHouse.controllers', 'ngRoute'])
      .config(['$routeProvider',
        ($routeProvider) ->
          $routeProvider
            .when '/home',
              { templateUrl: 'templates/home.html',            controller: 'HomeCtrl',      controllerAs: 'homeCtrl' }
            .when '/bidders',
              { templateUrl: 'templates/bidders.html',         controller: 'BiddersCtrl',   controllerAs: 'biddersCtrl' }
            .when '/items',
              { templateUrl: 'templates/items.html',           controller: 'ItemsCtrl',     controllerAs: 'itemsCtrl' }
            .when '/bidentry',
              { templateUrl: 'templates/bidentry.html',        controller: 'BidEntryCtrl',  controllerAs: 'bidEntryCtrl' }
            .when '/reconciliation',
              { templateUrl: 'templates/reconciliation.html',  controller: 'ReconCtrl',     controllerAs: 'reconCtrl' }
            .otherwise { redirectTo: '/home' }
      ])

    angular.bootstrap(document, ['AuctionHouse'])
