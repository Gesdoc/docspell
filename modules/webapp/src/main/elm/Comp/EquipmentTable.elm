module Comp.EquipmentTable exposing
    ( Model
    , Msg(..)
    , emptyModel
    , update
    , view
    )

import Api.Model.Equipment exposing (Equipment)
import Data.Flags exposing (Flags)
import Html exposing (..)
import Html.Attributes exposing (..)
import Html.Events exposing (onClick)


type alias Model =
    { equips : List Equipment
    , selected : Maybe Equipment
    }


emptyModel : Model
emptyModel =
    { equips = []
    , selected = Nothing
    }


type Msg
    = SetEquipments (List Equipment)
    | Select Equipment
    | Deselect


update : Flags -> Msg -> Model -> ( Model, Cmd Msg )
update _ msg model =
    case msg of
        SetEquipments list ->
            ( { model | equips = list, selected = Nothing }, Cmd.none )

        Select equip ->
            ( { model | selected = Just equip }, Cmd.none )

        Deselect ->
            ( { model | selected = Nothing }, Cmd.none )


view : Model -> Html Msg
view model =
    table [ class "ui very basic aligned table" ]
        [ thead []
            [ tr []
                [ th [ class "collapsing" ] []
                , th [] [ text "Name" ]
                ]
            ]
        , tbody []
            (List.map (renderEquipmentLine model) model.equips)
        ]


renderEquipmentLine : Model -> Equipment -> Html Msg
renderEquipmentLine model equip =
    tr
        [ classList [ ( "active", model.selected == Just equip ) ]
        ]
        [ td [ class "collapsing" ]
            [ a
                [ href "#"
                , class "ui basic small blue label"
                , onClick (Select equip)
                ]
                [ i [ class "edit icon" ] []
                , text "Edit"
                ]
            ]
        , td []
            [ text equip.name
            ]
        ]
