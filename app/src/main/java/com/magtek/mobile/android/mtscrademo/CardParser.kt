/*

//
//  CardParser.swift
//  Charge
//
//  Created on 8/10/17.
//  Copyright © 2017 SureSwift Capital Inc. All rights reserved.
//

import Foundation

struct CardInfo {
    let number: String
            let month: UInt
            let year: UInt
}

class CardParser {
    static func parse(_ str: String, ksn: String) -> CardInfo? {
        let d = DUKPT(bdk: "b2395cd7d466f6e1eb82602e8e69b750", ksn: ksn)
        let one = d?.decrypt(str)
        let cardPattern = "(?<=\\;)([0-9]+?)(?=\\=)"

        let expiryPattern = "(?<=\\=)([0-9]{4}?)"

        guard let track = one else { return nil }

        let _card = match(for: cardPattern, in: track)
        let _expiry = match(for: expiryPattern, in: track)


        guard let number = _card,
        let expiry = _expiry,
        let expiration = date(str: expiry) else { return nil }

        return CardInfo(number: number, month: expiration.0, year: expiration.1)
    }

    static private func match(for regex: String, in text: String) -> String? {
        do {
            let regex = try NSRegularExpression(pattern: regex)
                let nsString = text as NSString

                        let results = regex.firstMatch(in: text, range: NSRange(location: 0, length: nsString.length))
                return results.map { nsString.substring(with: $0.range)}
            } catch {
                return nil
            }
        }

        static private func date(str: String) -> (UInt, UInt)? {
            let _yearString = str.substring(location: 0, length: 2)
            let _monthString = str.substring(location: 2, length: 2)

            guard let monthString = _monthString, let yearString = _yearString else { return nil }

            let _month = UInt(monthString)
            let _year = UInt(yearString)

            guard let month = _month, let year = _year else { return nil }

            return (month, year + 2000)
        }
    }


 */