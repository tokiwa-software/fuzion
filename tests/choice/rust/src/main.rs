
enum Coin {
    Penny,
    Nickel,
    Dime,
    Quarter,
}

fn value_in_cents(coin: &Coin) -> u8 {
    match coin {
        Coin::Penny => 1,
        Coin::Nickel => 5,
        Coin::Dime => 10,
        Coin::Quarter => 25,
    }
}

fn main() {
   let coins = [
       Coin::Penny, Coin::Penny, Coin::Penny, Coin::Penny,
       Coin::Nickel,
       Coin::Dime, Coin::Dime,
       Coin::Quarter, Coin::Quarter, Coin::Quarter];
   let mut sum = 0;
   for c in coins.iter()
   {
     sum += value_in_cents(c);
   }
   println!("Sum is {} cents", sum);
}
