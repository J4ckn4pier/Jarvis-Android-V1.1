package com.jarvis.brain;
import java.util.List;
public final class MenuPriceQuoteTest {
 public static void main(String[] args) {
  MenuPriceQuote q=MenuPriceQuote.sum(List.of(new MenuPriceQuote.LineItem("ribeye",52),new MenuPriceQuote.LineItem("truffle mac",14)),List.of("drinks","tip"));
  String s=q.speak();
  check(s.contains("ribeye at $52"),"names ribeye price");
  check(s.contains("truffle mac at $14"),"names mac price");
  check(s.contains("$66 per person"),"one explicit total");
  check(s.contains("excluding drinks and tip"),"explicit exclusions");
  System.out.println("MenuPriceQuoteTest passed");
 }
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
