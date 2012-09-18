/*
BaseHList

WORK IN PROGRESS

Created in the faint hope that I could abstract out functionality from HList 
for the purpose of supporting CIList ('Common Interface List', which itself 
is a blind undertaking whose feasibility, usefulness and necessity are 
not something I'm very sure of).

- BaseHList should become a supertype for HList, CIList and possibly others.
  - In order to eliminate [ambiguity] between these sibling types, their 
    child types in turn should probably contain references to their 
    'mid-level base types', i.e. HNil and :: ('HCons') should have a  
    'type parent = HList'. Possibly HList itself should be the 'parent' of 
    itself. (Perhaps 'parent' is not the right word here...)
    - 'SubBaseHList'?
    - 'Family'?
- A whole bunch of traits used in implicit conversions will now have to be 
  adapted from hlist.scala
*/

trait BaseHList {
  type Family <: BaseHList
  type FamilyCons <: BaseHCons
  type FamilyNil <: BaseHNil
  val FamilyNil:FamilyNil
}
trait BaseHCons
trait BaseHNil
/**
 * Carrier for `HList` operations.
 * 
 * These methods are implemented here and pimped onto the minimal `HList` types to avoid issues that would otherwise be
 * caused by the covariance of `::[H, T]`.
 * 
 * @author Miles Sabin
 */
final class BaseHListOps[L <: BaseHList](l : L) {
  /**
   * Returns the head of this `HList`. Available only if there is evidence that this `HList` is composite.
   */
  def head(implicit c : IsHCons[L]) : c.H = c.head(l) 

  /**
   * Returns that tail of this `HList`. Available only if there is evidence that this `HList` is composite.
   */
  def tail(implicit c : IsHCons[L]) : c.T = c.tail(l)
  
  /**
   * Prepend the argument element to this `HList`.
   */
  def ::[H](h : H) : H :: L = shapeless.::(h, l)

  /**
   * Prepend the argument element to this `HList`.
   */
  def +:[H](h : H) : H :: L = shapeless.::(h, l)
  
  /**
   * Append the argument element to this `HList`.
   */
  def :+[T](t : T)(implicit prepend : Prepend[L, T :: L#FamilyHNil]) : prepend.Out = 
    prepend(l, t :: l.FamilyHNil)
  
  /**
   * Append the argument `HList` to this `HList`.
   */
  def ++[S <: L#Family](suffix : S)(implicit prepend : Prepend[L, S]) : prepend.Out = prepend(l, suffix)
  
  /**
   * Prepend the argument `HList` to this `HList`.
   */
  def ++:[P <: L#Family](prefix : P)(implicit prepend : Prepend[P, L]) : prepend.Out = prepend(prefix, l)
  
  /**
   * Prepend the argument `HList` to this `HList`.
   */
  def :::[P <: L#Family](prefix : P)(implicit prepend : Prepend[P, L]) : prepend.Out = prepend(prefix, l)
  
  /**
   * Prepend the reverse of the argument `HList` to this `HList`.
   */
  def reverse_:::[P <: L#Family](prefix : P)(implicit prepend : ReversePrepend[P, L]) : prepend.Out = 
    prepend(prefix, l)

  /**
   * Returns the ''nth'' of this `HList`. An explicit type argument must be provided. Available only if there is
   * evidence that this `HList` has at least ''n'' elements.
   */
  def apply[N <: Nat](implicit at : At[L, N]) : at.Out = at(l)

  /**
   * Returns the ''nth'' of this `HList`. Available only if there is evidence that this `HList` has at least ''n''
   * elements.
   */
  def apply[N <: Nat](n : N)(implicit at : At[L, N]) : at.Out = at(l)
  
  /**
   * Returns the last element of this `HList`. Available only if there is evidence that this `HList` is composite.
   */
  def last(implicit last : Last[L]) : last.Out = last(l)

  /**
   * Returns an `HList` consisting of all the elements of this `HList` except the last. Available only if there is
   * evidence that this `HList` is composite.
   */
  def init(implicit init : Init[L]) : init.Out = init(l)
  
  /**
   * Returns the first element of type `U` of this `HList`. An explicit type argument must be provided. Available only
   * if there is evidence that this `HList` has an element of type `U`.
   */
  def select[U](implicit selector : Selector[L, U]) : U = selector(l)

  /**
   * Returns all elements of type `U` of this `HList`. An explicit type argument must be provided.
   */
  def filter[U](implicit filter : Filter[L, U]) : filter.Out  = filter(l)

  /**
   * Returns all elements of type different than `U` of this `HList`. An explicit type argument must be provided.
   */
  def filterNot[U](implicit filter : FilterNot[L, U]) : filter.Out  = filter(l)
  
  /**
   * Returns the first element of type `U` of this `HList` plus the remainder of the `HList`. An explicit type argument
   * must be provided. Available only if there is evidence that this `HList` has an element of type `U`.
   * 
   * The `Elem` suffix is here to avoid creating an ambiguity with RecordOps#remove and should be removed if
   * SI-5414 is resolved in a way which eliminates the ambiguity.
   */
  def removeElem[U](implicit remove : Remove[U, L]) : (U, remove.Out) = remove(l)
  
  /**
   * Returns the first elements of this `HList` that have types in `SL` plus the remainder of the `HList`. An expicit
   * type argument must be provided. Available only if there is evidence that this `HList` contains elements with
   * types in `SL`.
   */
  def removeAll[SL <: L#Family](implicit removeAll : RemoveAll[SL, L]) : (SL, removeAll.Out) = removeAll(l)

  /**
   * Replaces the first element of type `U` of this `HList` with the supplied value, also of type `U` returning both
   * the replaced element and the updated `HList`. Available only if there is evidence that this `HList` has an element
   * of type `U`.
   */
  def replace[U](u : U)(implicit replacer : Replacer[L, U, U]) : (U, replacer.Out) = replacer(l, u)
  
  class ReplaceTypeAux[U] {
    def apply[V](v : V)(implicit replacer : Replacer[L, U, V]) : (U, replacer.Out) = replacer(l, v)
  }
  
  /**
   * Replaces the first element of type `U` of this `HList` with the supplied value of type `V`, return both the
   * replaced element and the updated `HList`. An explicit type argument must be provided for `U`. Available only if
   * there is evidence that this `HList` has an element of type `U`.
   */
  def replaceType[U] = new ReplaceTypeAux[U]
  
  /**
   * Replaces the first element of type `U` of this `HList` with the supplied value, also of type `U`. Available only
   * if there is evidence that this `HList` has an element of type `U`.
   * 
   * The `Elem` suffix is here to avoid creating an ambiguity with RecordOps#updated and should be removed if
   * SI-5414 is resolved in a way which eliminates the ambiguity.
   */
  def updatedElem[U](u : U)
    (implicit replacer : Replacer[L, U, U]) : replacer.Out = replacer(l, u)._2
  
  class UpdatedTypeAux[U] {
    def apply[V](v : V)(implicit replacer : Replacer[L, U, V]) : replacer.Out = replacer(l, v)._2
  }
  
  /**
   * Replaces the first element of type `U` of this `HList` with the supplied value of type `V`. An explicit type
   * argument must be provided for `U`. Available only if there is evidence that this `HList` has an element of
   * type `U`.
   */
  def updatedType[U] = new UpdatedTypeAux[U]
  
  class UpdatedAtAux[N <: Nat] {
    def apply[U](u : U)(implicit replacer : ReplaceAt[L, N, U]) : replacer.Out = replacer(l, u)._2
  }
  
  /**
   * Replaces the first element of type `U` of this `HList` with the supplied value of type `V`. An explicit type
   * argument must be provided for `U`. Available only if there is evidence that this `HList` has an element of
   * type `U`.
   */
  def updatedAt[N <: Nat] = new UpdatedAtAux[N]
  
  /**
   * Returns the first ''n'' elements of this `HList`. An explicit type argument must be provided. Available only if
   * there is evidence that this `HList` has at least ''n'' elements.
   */
  def take[N <: Nat](implicit take : Take[L, N]) : take.Out = take(l)

  /**
   * Returns the first ''n'' elements of this `HList`. Available only if there is evidence that this `HList` has at
   * least ''n'' elements.
   */
  def take[N <: Nat](n : N)(implicit take : Take[L, N]) : take.Out = take(l)
  
  /**
   * Returns all but the  first ''n'' elements of this `HList`. An explicit type argument must be provided. Available
   * only if there is evidence that this `HList` has at least ''n'' elements.
   */
  def drop[N <: Nat](implicit drop : Drop[L, N]) : drop.Out = drop(l)

  /**
   * Returns all but the  first ''n'' elements of this `HList`. Available only if there is evidence that this `HList`
   * has at least ''n'' elements.
   */
  def drop[N <: Nat](n : N)(implicit drop : Drop[L, N]) : drop.Out = drop(l)
  
  /**
   * Splits this `HList` at the ''nth'' element, returning the prefix and suffix as a pair. An explicit type argument
   * must be provided. Available only if there is evidence that this `HList` has at least ''n'' elements.
   */
  def split[N <: Nat](implicit split : Split[L, N]) : split.Out = split(l)

  /**
   * Splits this `HList` at the ''nth'' element, returning the prefix and suffix as a pair. Available only if there is
   * evidence that this `HList` has at least ''n'' elements.
   */
  def split[N <: Nat](n : N)(implicit split : Split[L, N]) : split.Out = split(l)
  
  /**
   * Splits this `HList` at the ''nth'' element, returning the reverse of the prefix and suffix as a pair. An explicit
   * type argument must be provided. Available only if there is evidence that this `HList` has at least ''n'' elements.
   */
  def reverse_split[N <: Nat](implicit split : ReverseSplit[L, N]) : split.Out = split(l)

  /**
   * Splits this `HList` at the ''nth'' element, returning the reverse of the prefix and suffix as a pair. Available
   * only if there is evidence that this `HList` has at least ''n'' elements.
   */
  def reverse_split[N <: Nat](n : N)(implicit split : ReverseSplit[L, N]) : split.Out = split(l)

  /**
   * Splits this `HList` at the first occurrence of an element of type `U`, returning the prefix and suffix as a pair.
   * An explicit type argument must be provided. Available only if there is evidence that this `HList` has an element
   * of type `U`.
   */
  def splitLeft[U](implicit splitLeft : SplitLeft[L, U]) : splitLeft.Out = splitLeft(l)

  /**
   * Splits this `HList` at the first occurrence of an element of type `U`, returning reverse of the prefix and suffix
   * as a pair. An explicit type argument must be provided. Available only if there is evidence that this `HList` has
   * an element of type `U`.
   */
  def reverse_splitLeft[U](implicit splitLeft : ReverseSplitLeft[L, U]) : splitLeft.Out = splitLeft(l)

  /**
   * Splits this `HList` at the last occurrence of an element of type `U`, returning the prefix and suffix as a pair.
   * An explicit type argument must be provided. Available only if there is evidence that this `HList` has an element
   * of type `U`.
   */
  def splitRight[U](implicit splitRight : SplitRight[L, U]) : splitRight.Out = splitRight(l)

  /**
   * Splits this `HList` at the last occurrence of an element of type `U`, returning reverse of the prefix and suffix
   * as a pair. An explicit type argument must be provided. Available only if there is evidence that this `HList` has
   * an element of type `U`.
   */
  def reverse_splitRight[U](implicit splitRight : ReverseSplitRight[L, U]) : splitRight.Out = splitRight(l)

  /**
   * Reverses this `HList`.
   */
  def reverse(implicit reverse : Reverse[L]) : reverse.Out = reverse(l)

  /**
   * Maps a higher rank function across this `HList`.
   */
  def map[HF](f : HF)(implicit mapper : Mapper[HF, L]) : mapper.Out = mapper(l)

  /**
   * Flatmaps a higher rank function across this `HList`.
   */
  def flatMap[HF](f : HF)(implicit mapper : FlatMapper[HF, L]) : mapper.Out = mapper(l)

  /**
   * Replaces each element of this `HList` with a constant value.
   */
  def mapConst[C](c : C)(implicit mapper : ConstMapper[C, L]) : mapper.Out = mapper(c, l)
  
  /**
   * Maps a higher rank function ''f'' across this `HList` and folds the result using monomorphic combining operator
   * `op`. Available only if there is evidence that the result type of `f` at each element conforms to the argument
   * type of ''op''.
   */
  def foldMap[R, HF](z : R)(f : HF)(op : (R, R) => R)(implicit folder : MapFolder[L, R, HF]) : R = folder(l, z, op)
  
  /**
   * Computes a left fold over this `HList` using the polymorphic binary combining operator `op`. Available only if
   * there is evidence `op` can consume/produce all the partial results of the appropriate types.
   */
  def foldLeft[R, HF](z : R)(op : HF)(implicit folder : LeftFolder[L, R, HF]) : folder.Out = folder(l, z)
  
  /**
   * Computes a right fold over this `HList` using the polymorphic binary combining operator `op`. Available only if
   * there is evidence `op` can consume/produce all the partial results of the appropriate types.
   */
  def foldRight[R, HF](z : R)(op : HF)(implicit folder : RightFolder[L, R, HF]) : folder.Out = folder(l, z)
  
  /**
   * Computes a left reduce over this `HList` using the polymorphic binary combining operator `op`. Available only if
   * there is evidence that this `HList` has at least one element and that `op` can consume/produce all the partial
   * results of the appropriate types.
   */
  def reduceLeft[HF](op : HF)(implicit reducer : LeftReducer[L, HF]) : reducer.Out = reducer(l)
  
  /**
   * Computes a right reduce over this `HList` using the polymorphic binary combining operator `op`. Available only if
   * there is evidence that this `HList` has at least one element and that `op` can consume/produce all the partial
   * results of the appropriate types.
   */
  def reduceRight[HF](op : HF)(implicit reducer : RightReducer[L, HF]) : reducer.Out = reducer(l)
  
  /**
   * Zips this `HList` with its argument `HList` returning an `HList` of pairs.
   */
  def zip[R <: L#Family](r : R)(implicit zipper : Zip[L :: R :: L#FamilyHNil]) : zipper.Out = 
    zipper(l :: r :: l.FamilyHNil)
  
  /**
   * Zips this `HList` of monomorphic function values with its argument `HList` of correspondingly typed function
   * arguments returning the result of each application as an `HList`. Available only if there is evidence that the
   * corresponding function and argument elements have compatible types.
   */
  def zipApply[A <: L#Family](a : A)(implicit zipper : ZipApply[L, A]) : zipper.Out = zipper(l, a)

  /**
   * Zips this `HList` of `HList`s returning an `HList` of tuples. Available only if there is evidence that this
   * `HList` has `HList` elements.
   */
  def zipped(implicit zipper : Zip[L]) : zipper.Out = zipper(l)

  /**
   * Unzips this `HList` of tuples returning a tuple of `HList`s. Available only if there is evidence that this
   * `HList` has tuple elements.
   */
  def unzipped(implicit unzipper : Unzip[L]) : unzipper.Out = unzipper(l)
  
  /**
   * Zips this `HList` with its argument `HList` of `HList`s, returning an `HList` of `HList`s with each element of
   * this `HList` prepended to the corresponding `HList` element of the argument `HList`.
   */
  def zipOne[T <: L#Family](t : T)(implicit zipOne : ZipOne[L, T]) : zipOne.Out = zipOne(l, t)
  
  /**
   * Transposes this `HList`.
   */
  def transpose(implicit transpose : Transposer[L]) : transpose.Out = transpose(l)

  /**
   * Returns an `HList` typed as a repetition of the least upper bound of the types of the elements of this `HList`.
   */
  def unify(implicit unifier : Unifier[L]) : unifier.Out = unifier(l)

  /**
   * Converts this `HList` to a correspondingly typed tuple.
   */
  def tupled(implicit tupler : Tupler[L]) : tupler.Out = tupler(l)
  
  /**
   * Compute the length of this `Hlist`.
   */
  def length(implicit length : Length[L]) : length.Out = length()
  
  /**
   * Converts this `HList` to an ordinary `List` of elements typed as the least upper bound of the types of the elements
   * of this `HList`.
   */
  def toList[Lub](implicit toList : ToList[L, Lub]) : List[Lub] = toList(l)
  
  /**
   * Converts this `HList` to an `Array` of elements typed as the least upper bound of the types of the elements
   * of this `HList`.
   * 
   * It is advisable to specify the type parameter explicitly, because for many reference types, case classes in
   * particular, the inferred type will be too precise (ie. `Product with Serializable with CC` for a typical case class
   * `CC`) which interacts badly with the invariance of `Array`s.
   */
  def toArray[Lub](implicit toArray : ToArray[L, Lub]) : Array[Lub] = toArray(0, l)
}

