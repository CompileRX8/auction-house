define ['angular'],
  (angular) ->
    mod = angular.module('AuctionHouse.directives', [])

    integerRegex = /^-?\d+$/
    mod.directive 'integer', ->
      {
        require: 'ngModel',
        link: (scope, elm, attrs, ctrl) ->
          ctrl.$parsers.unshift( (viewValue) ->
            if(integerRegex.test(viewValue))
              ctrl.$setValidity('integer', true)
              viewValue
            else
              ctrl.$setValidity('integer', false)
              undefined
          )
      }

    floatRegex = /^-?\d+([.,]\d+)?$/
    mod.directive 'smartFloat', ->
      {
        require: 'ngModel',
        link: (scope, elm, attrs, ctrl) ->
          ctrl.$parsers.unshift( (viewValue) ->
            if(floatRegex.test(viewValue))
              ctrl.$setValidity('float', true)
              viewValue
            else
              ctrl.$setValidity('float', false)
              undefined
          )
      }
